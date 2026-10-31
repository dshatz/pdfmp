package com.dshatz.pdfmp

import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.pdfium.Availability
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.Pdfium
import com.dshatz.pdfmp.pdfium.PdfiumBitmap
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface IPDfRenderer: AutoCloseable {

    suspend fun getPageCount(): Int
    suspend fun renderTile(tile: PdfTile, buffer: ImageBuffer)
    suspend fun getPageRatio(pageIndex: Int): Float
    suspend fun openDocument(): Result<Unit>
}
abstract class PdfiumRenderer: AutoCloseable, IPDfRenderer {
    private val drawTileFrames: Boolean = false

    val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    protected lateinit var doc: DocHandle

    protected fun isDocLoaded(): Boolean = this::doc.isInitialized

    protected fun isDocInitialized() = this::doc.isInitialized

    protected val pageHandleCache = mutableMapOf<Int, PageHandle>()

    protected fun ImageBuffer.fpdfBitmap(address: Long): PdfiumBitmap {
        return Pdfium.BitmapCreateExBGRA(
            DataPointer.fromLongPointer(address),
            width,
            height,
            stride
        ) ?: error("Failed to create combined bitmap wrapper")
    }

    /**
     * Renders the [tile] of [page] into [buffer] using [com.dshatz.pdfmp.pdfium.PdfiumRenderCore.renderTile], as a
     * single operation (a single worker call on wasm).
     */
    protected abstract suspend fun renderTileInto(buffer: ImageBuffer, page: PageHandle, tile: PdfTile, frameColor: UInt?)

    /**
     * Renders [page] at [dimensions] into [buffer] using [com.dshatz.pdfmp.pdfium.PdfiumRenderCore.renderPage].
     * The pdfium bitmap must have the size of [buffer], which can differ from [dimensions].
     */
    protected abstract suspend fun renderPageInto(buffer: ImageBuffer, page: PageHandle, dimensions: PageDimensions)

    /** [com.dshatz.pdfmp.pdfium.PdfiumRenderCore.pageRatio] of [page]. */
    protected abstract suspend fun pageRatio(page: PageHandle): Float

    /** How many renders currently use each open page. Only touched on [pdfiumDispatcher]. */
    private val pageUsers = mutableMapOf<Int, Int>()

    /** Two renders must not both open the same page: the second handle would replace the first in the cache and leak. */
    private val pageOpenMutex = Mutex()

    /**
     * Runs [block] with the page open. pdfium keeps the parsed content and decoded images of every open page,
     * so once no render uses a page any more, only the [MAX_IDLE_OPEN_PAGES] most recently used stay open.
     */
    private suspend fun <T> withPage(pageIndex: Int, block: suspend (PageHandle) -> T): T {
        val page = pageOpenMutex.withLock {
            // Re-insert so pageHandleCache stays ordered from least to most recently used.
            val handle = pageHandleCache.remove(pageIndex) ?: openPageWhenAvailable(pageIndex) ?: error(
                "Failed to load page $pageIndex: ${getLastErrorForCustomSource() ?: Pdfium.GetLastError()}"
            )
            pageHandleCache[pageIndex] = handle
            pageUsers[pageIndex] = (pageUsers[pageIndex] ?: 0) + 1
            handle
        }
        try {
            return block(page)
        } finally {
            // No suspension here, so this also runs to completion for cancelled renders.
            val users = pageUsers.getValue(pageIndex) - 1
            if (users == 0) pageUsers.remove(pageIndex) else pageUsers[pageIndex] = users
            closeIdlePages()
        }
    }

    private fun closeIdlePages() {
        val idle = pageHandleCache.keys.filter { it !in pageUsers } // least recently used first
        idle.take((idle.size - MAX_IDLE_OPEN_PAGES).coerceAtLeast(0)).forEach { pageIndex ->
            pageHandleCache.remove(pageIndex)?.closePage()
        }
    }

    protected abstract suspend fun DocHandle.openPage(pageIndex: Int): PageHandle?
    protected abstract fun PageHandle.closePage()

    private suspend fun openPageWhenAvailable(pageIndex: Int): PageHandle? {
        if (progressiveSource != null) awaitAvailable { progressiveIsPageAvail(pageIndex) }
        return doc.openPage(pageIndex)
    }

    // Progressive loading of custom sources, see com.dshatz.pdfmp.pdfium.ProgressiveDocument.

    private var progressiveSource: CustomPdfSourceAdapter? = null
    private var documentLength = 0L

    /** Opens [source] reading only what pdfium needs: first for the document, then for each page as it opens. */
    protected suspend fun openProgressive(source: CustomPdfSourceAdapter) = withContext(pdfiumDispatcher) {
        documentLength = source.getDocumentLength()
        require(documentLength <= Int.MAX_VALUE) { "Documents over 2 GB are not supported" }
        progressiveSource = source
        progressiveOpen(documentLength.toInt(), PROGRESSIVE_CHUNK_SIZE)
        awaitAvailable { progressiveIsDocAvail() }
        doc = progressiveDocument() ?: error("FPDFAvail_GetDocument returned null")
    }

    /** Fetches the chunks [availability] reports missing until it reports the data available. */
    private suspend fun awaitAvailable(availability: suspend () -> Availability) {
        while (true) {
            val result = availability()
            when (result.status) {
                Availability.AVAILABLE -> return
                Availability.NOT_AVAILABLE -> {
                    check(result.missingChunks.isNotEmpty()) { "pdfium is missing data but asked for none" }
                    fetchChunks(result.missingChunks)
                }
                else -> error("Invalid PDF data")
            }
        }
    }

    /** One read per run of consecutive chunks, all in parallel. */
    private suspend fun fetchChunks(chunks: List<Int>) = coroutineScope {
        chunks.consecutiveRuns().map { run -> async { fetchRun(run) } }.awaitAll()
    }

    private suspend fun fetchRun(chunks: IntRange) {
        val offset = chunks.first.toLong() * PROGRESSIVE_CHUNK_SIZE
        val size = minOf((chunks.last - chunks.first + 1).toLong() * PROGRESSIVE_CHUNK_SIZE, documentLength - offset)
        progressiveFill(chunks.first, size.toInt()) { buffer -> progressiveSource!!.readBlock(offset, buffer) }
    }

    // Platform side of progressive loading: the com.dshatz.pdfmp.pdfium.ProgressiveDocument calls, in-process or in
    // the web worker. Called on [pdfiumDispatcher].

    protected abstract suspend fun progressiveOpen(length: Int, chunkSize: Int)
    protected abstract suspend fun progressiveIsDocAvail(): Availability
    protected abstract suspend fun progressiveIsPageAvail(pageIndex: Int): Availability
    protected abstract suspend fun progressiveDocument(): DocHandle?

    /** Lets [read] write [size] bytes starting at chunk [firstChunk], then makes them available to pdfium. */
    protected abstract suspend fun progressiveFill(firstChunk: Int, size: Int, read: suspend (ByteBuffer) -> Unit)

    protected fun getLastErrorForCustomSource(): String? {
        return null // TODO
//        return (source as? PdfSource.Custom)?.customSourceDescriptor?.sourceAdapter?.getLastError()
    }

    abstract override fun close()

    override suspend fun getPageCount(): Int = withContext(pdfiumDispatcher) {
        Pdfium.GetPageCount(doc)
    }

    override suspend fun renderTile(tile: PdfTile, buffer: ImageBuffer) {
        withContext(pdfiumDispatcher) {
            withPage(tile.key.page) { page ->
                val frameColor = if (drawTileFrames) {
                    if (tile.key.page % 2 == 0) 0xFF0000FFu else 0xFFFF0000u
                } else null
                renderTileInto(buffer, page, tile, frameColor)
            }
        }
    }

    override suspend fun getPageRatio(pageIndex: Int): Float = withContext(pdfiumDispatcher) {
        val result = runCatching {
            withPage(pageIndex) { page ->
                pageRatio(page)
            }
        }.recoverCatching {
            val customSourceError = getLastErrorForCustomSource()
            if (customSourceError != null) {
                error(customSourceError)
            } else throw it
        }
        result.getOrThrow()
    }

    suspend fun render(page: Int, dimensions: PageDimensions, buffer: ImageBuffer) = withContext(pdfiumDispatcher) {
        withPage(page) { pageHandle ->
            renderPageInto(buffer, pageHandle, dimensions)
        }
    }

    companion object {
        /** The smallest unit read from a custom source. */
        private const val PROGRESSIVE_CHUNK_SIZE = 64 * 1024

        /** Unused pages kept open, so scrolling back and forth doesn't reparse pages all the time. */
        private const val MAX_IDLE_OPEN_PAGES = 2
    }
}

/** Runs of consecutive values of this sorted list, e.g. [1, 2, 3, 7, 8] -> [1..3, 7..8]. */
private fun List<Int>.consecutiveRuns(): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = first()
    var end = start
    for (value in drop(1)) {
        if (value == end + 1) end = value else {
            runs += start..end
            start = value
            end = value
        }
    }
    runs += start..end
    return runs
}

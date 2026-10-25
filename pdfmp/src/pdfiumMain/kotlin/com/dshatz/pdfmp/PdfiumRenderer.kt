package com.dshatz.pdfmp

import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.Pdfium
import com.dshatz.pdfmp.pdfium.PdfiumBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
            val handle = pageHandleCache.remove(pageIndex) ?: doc.openPage(pageIndex) ?: error(
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
        /** Unused pages kept open, so scrolling back and forth doesn't reparse pages all the time. */
        private const val MAX_IDLE_OPEN_PAGES = 2
    }
}

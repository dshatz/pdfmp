package com.dshatz.pdfmp

import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.PdfClip
import com.dshatz.pdfmp.pdfium.PdfMatrix
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

    protected abstract suspend fun <R> ImageBuffer.withFpdfBitmap(block: suspend (bitmap: PdfiumBitmap) -> R)

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

    /**
     * Draws a 1-pixel (or customizable thickness) frame around the perimeter of the bitmap.
     *
     * @param color ARGB color format (e.g., 0xFF0000FFu for Blue, 0xFFFF0000u for Red)
     * @param strokeWidth Border width in pixels
     */
    private suspend fun PdfiumBitmap.drawFrame(
        width: Int,
        height: Int,
        color: UInt = 0xFF0000FFu, // Default: Solid Blue
        strokeWidth: Int = 2
    ) {
        // Top border
        fillRect(1, 1, width, strokeWidth, color)
        // Bottom border
        fillRect(1, height - strokeWidth - 1, width, strokeWidth, color)
        // Left border
        fillRect(1, 1, strokeWidth, height, color)
        // Right border
        fillRect(width - strokeWidth - 1, 1, strokeWidth, height, color)
    }

    abstract override fun close()

    abstract suspend fun PdfiumBitmap.fillRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    )

    abstract suspend fun PdfiumBitmap.destroy()

    protected abstract suspend fun RenderPageBitmap(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )

    protected abstract suspend fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    )


    override suspend fun getPageCount(): Int = withContext(pdfiumDispatcher) {
        Pdfium.GetPageCount(doc)
    }

    override suspend fun renderTile(tile: PdfTile, buffer: ImageBuffer) {
        withContext(pdfiumDispatcher) {
            /*block(500)*/
            buffer.withFpdfBitmap { bitmap ->
                withPage(tile.key.page) { page ->
                    val result = runCatching {
                        val pageW: Float = page.getPageWidth()
                        val pageH: Float = page.getPageHeight()

                        val bgWidth = (tile.scaledPage.width - tile.key.x).coerceIn(0, PdfTile.WIDTH)
                        val bgHeight = (tile.scaledPage.height - tile.key.y).coerceIn(0, PdfTile.HEIGHT)

                        bitmap.fillRect(
                            0,
                            0,
                            PdfTile.WIDTH,
                            PdfTile.HEIGHT,
                            0x00000000u // transparent black to reset the pixels already in the buffer.
                        )

                        bitmap.fillRect(
                            0,
                            0,
                            bgWidth,
                            bgHeight,
                            0xFFFFFFFFu
                        )
                        val matrix = PdfMatrix.Identity
                            .scale(tile.scaledPage.width / pageW, tile.scaledPage.height / pageH)
                            .translate(-tile.key.x.toFloat(), -tile.key.y.toFloat())

                        val clip = PdfClip(
                            0f,
                            0f,
                            PdfTile.WIDTH.toFloat(),
                            PdfTile.HEIGHT.toFloat()
                        )
                        RenderPageBitmapWithMatrix(
                            bitmap,
                            page,
                            matrix,
                            clip,
                            0
                        )
                        if (drawTileFrames) {
                            bitmap.drawFrame(
                                PdfTile.WIDTH,
                                PdfTile.HEIGHT,
                                color = if (tile.key.page % 2 == 0) 0xFF0000FFu else 0xFFFF0000u
                            )
                        }
                    }
                    result.getOrThrow()
                }
            }
        }
    }

    abstract suspend fun PageHandle.getPageWidth(): Float
    abstract suspend fun PageHandle.getPageHeight(): Float

    override suspend fun getPageRatio(pageIndex: Int): Float = withContext(pdfiumDispatcher) {
        val result = runCatching {
            withPage(pageIndex) { page ->
                val width = page.getPageWidth()
                val height = page.getPageHeight()
                if (width <= 0f || height <= 0f) {
                    error("Invalid size: $width x $height")
                }
                width / height
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
        buffer.withFpdfBitmap { bitmap ->
            withPage(page) { pageHandle ->
                val result = runCatching {
                    bitmap.fillRect(
                        0,
                        0,
                        dimensions.width,
                        dimensions.height,
                        0xFFFFFFFFu
                    )
                    RenderPageBitmap(
                        bitmap,
                        pageHandle,
                        0,
                        0,
                        dimensions.width,
                        dimensions.height,
                        0,
                        0
                    )
                }
                result.getOrThrow()
            }
        }
    }

    companion object {
        /** Unused pages kept open, so scrolling back and forth doesn't reparse pages all the time. */
        private const val MAX_IDLE_OPEN_PAGES = 2
    }
}

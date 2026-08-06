package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.PdfClip
import com.dshatz.pdfmp.pdfium.PdfMatrix
import com.dshatz.pdfmp.pdfium.Pdfium
import com.dshatz.pdfmp.pdfium.PdfiumBitmap
import com.dshatz.pdfmp.pdfium.closePage
import com.dshatz.pdfmp.pdfium.destroy
import com.dshatz.pdfmp.pdfium.fillRect
import com.dshatz.pdfmp.pdfium.getPageHeight
import com.dshatz.pdfmp.pdfium.getPageWidth
import com.dshatz.pdfmp.pdfium.openPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class PdfiumRenderer: AutoCloseable {
    private val drawTileFrames: Boolean = true

    val pdfiumDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    protected lateinit var doc: DocHandle

    protected fun isDocInitialized() = this::doc.isInitialized

    protected fun ImageBuffer.fpdfBitmap(address: Long): PdfiumBitmap {
        return Pdfium.BitmapCreateExBGRA(
            DataPointer.fromLongPointer(address),
            width,
            height,
            stride
        ) ?: error("Failed to create combined bitmap wrapper")
    }

    abstract fun <R> ImageBuffer.withFpdfBitmap(block: (bitmap: PdfiumBitmap) -> R)

    private fun DocHandle.openPageOrThrow(pageIndex: Int): PageHandle {
        return this.openPage(pageIndex) ?: run {
            error("Failed to load page $pageIndex: ${getLastErrorForCustomSource() ?: Pdfium.GetLastError()}")
        }
    }

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
    private fun PdfiumBitmap.drawFrame(
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

    suspend fun getPageCount(): Int = withContext(pdfiumDispatcher) {
        Pdfium.GetPageCount(doc)
    }

    suspend fun renderTile(tile: PdfTile, buffer: ImageBuffer) {
        withContext(pdfiumDispatcher) {
            buffer.withFpdfBitmap { bitmap ->
                val page = doc.openPageOrThrow(tile.key.page)
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
                    Pdfium.RenderPageBitmapWithMatrix(
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
                page.closePage()
                result.getOrThrow()
            }
        }
    }

    suspend fun getPageRatio(pageIndex: Int): Float = withContext(pdfiumDispatcher) {
        val result = runCatching {
            val page = doc.openPageOrThrow(pageIndex)
            try {
                val width = page.getPageWidth()
                val height = page.getPageHeight()
                if (width <= 0f || height <= 0f) {
                    error("Invalid size: $width x $height")
                }
                width / height
            } finally {
                page.closePage()
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
            val page = doc.openPageOrThrow(page)
            val result = runCatching {
                bitmap.fillRect(
                    0,
                    0,
                    dimensions.width,
                    dimensions.height,
                    0xFFFFFFFFu
                )
                Pdfium.RenderPageBitmap(
                    bitmap,
                    page,
                    0,
                    0,
                    dimensions.width,
                    dimensions.height,
                    0,
                    0
                )
            }
            page.closePage()
            result.getOrThrow()
        }
    }
}
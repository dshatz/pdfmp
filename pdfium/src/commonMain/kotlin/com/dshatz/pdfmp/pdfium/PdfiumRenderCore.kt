package com.dshatz.pdfmp.pdfium

/** pdfium's `FPDF_ANNOT`: also draw annotations. Form widgets are not included, they need `FPDF_FFLDraw`. */
private const val FPDF_ANNOT = 0x01

/**
 * Rendering steps shared by all platforms. Runs wherever pdfium lives: in-process on native, inside the web worker
 * on wasm, so that a whole render is a single call across the worker boundary.
 */
object PdfiumRenderCore {

    fun pageRatio(pdfium: IPdfium, page: PageHandle): Float {
        val width = pdfium.GetPageWidthF(page)
        val height = pdfium.GetPageHeightF(page)
        if (width <= 0f || height <= 0f) {
            error("Invalid size: $width x $height")
        }
        return width / height
    }

    /** Renders the whole page at [width] x [height] into the top-left corner of [bitmap]. */
    fun renderPage(pdfium: IPdfium, bitmap: PdfiumBitmap, page: PageHandle, width: Int, height: Int) {
        pdfium.BitmapFillRect(bitmap, 0, 0, width, height, 0xFFFFFFFFu)
        pdfium.RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, FPDF_ANNOT)
    }

    /**
     * Renders the [tileWidth] x [tileHeight] area at ([x], [y]) of the page scaled to [scaledPageWidth] x
     * [scaledPageHeight] into [bitmap]. [frameColor] draws a debug frame around the tile.
     */
    fun renderTile(
        pdfium: IPdfium,
        bitmap: PdfiumBitmap,
        page: PageHandle,
        x: Int,
        y: Int,
        tileWidth: Int,
        tileHeight: Int,
        scaledPageWidth: Int,
        scaledPageHeight: Int,
        frameColor: UInt?,
    ) {
        val pageW = pdfium.GetPageWidthF(page)
        val pageH = pdfium.GetPageHeightF(page)

        val bgWidth = (scaledPageWidth - x).coerceIn(0, tileWidth)
        val bgHeight = (scaledPageHeight - y).coerceIn(0, tileHeight)

        // Transparent black to reset the pixels already in the buffer.
        pdfium.BitmapFillRect(bitmap, 0, 0, tileWidth, tileHeight, 0x00000000u)
        pdfium.BitmapFillRect(bitmap, 0, 0, bgWidth, bgHeight, 0xFFFFFFFFu)

        val matrix = PdfMatrix.Identity
            .scale(scaledPageWidth / pageW, scaledPageHeight / pageH)
            .translate(-x.toFloat(), -y.toFloat())
        val clip = PdfClip(0f, 0f, tileWidth.toFloat(), tileHeight.toFloat())
        pdfium.RenderPageBitmapWithMatrix(bitmap, page, matrix, clip, FPDF_ANNOT)

        if (frameColor != null) {
            drawFrame(pdfium, bitmap, tileWidth, tileHeight, frameColor)
        }
    }

    /**
     * Draws a frame around the perimeter of the bitmap.
     *
     * @param color ARGB color format (e.g., 0xFF0000FFu for Blue, 0xFFFF0000u for Red)
     * @param strokeWidth Border width in pixels
     */
    private fun drawFrame(
        pdfium: IPdfium,
        bitmap: PdfiumBitmap,
        width: Int,
        height: Int,
        color: UInt,
        strokeWidth: Int = 2
    ) {
        // Top border
        pdfium.BitmapFillRect(bitmap, 1, 1, width, strokeWidth, color)
        // Bottom border
        pdfium.BitmapFillRect(bitmap, 1, height - strokeWidth - 1, width, strokeWidth, color)
        // Left border
        pdfium.BitmapFillRect(bitmap, 1, 1, strokeWidth, height, color)
        // Right border
        pdfium.BitmapFillRect(bitmap, width - strokeWidth - 1, 1, strokeWidth, height, color)
    }
}

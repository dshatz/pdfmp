package com.dshatz.pdfmp.pdfium

expect class PdfiumBitmap {
}

fun PdfiumBitmap.destroy() = Pdfium.BitmapDestroy(this)
fun PdfiumBitmap.fillRect(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    color: UInt
) = Pdfium.BitmapFillRect(this, left, top, width, height, color)
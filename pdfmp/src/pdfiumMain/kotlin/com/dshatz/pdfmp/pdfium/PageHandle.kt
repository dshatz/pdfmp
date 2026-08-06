package com.dshatz.pdfmp.pdfium

expect class PageHandle {
}

fun PageHandle.getPageWidth(): Float = Pdfium.GetPageWidthF(this)
fun PageHandle.getPageHeight(): Float = Pdfium.GetPageHeightF(this)
fun PageHandle.closePage() = Pdfium.ClosePage(this)
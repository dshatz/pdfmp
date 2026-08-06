package com.dshatz.pdfmp.pdfium

expect class DocHandle

fun DocHandle.openPage(page: Int): PageHandle? {
    return Pdfium.LoadPage(this, page)
}

fun DocHandle.close() = Pdfium.CloseDocument(this)
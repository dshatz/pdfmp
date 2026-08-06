package com.dshatz.pdfmp.pdfium

expect object Pdfium {
    fun InitLibrary()
    fun LoadMemDocument(data: DataPointer, size: Int, password: String?): DocHandle?
    fun LoadDocument(path: String, password: String?): DocHandle?
    fun LoadCustomDocument(access: FileAccess, password: String?): DocHandle?
    fun GetPageCount(document: DocHandle): Int
    fun LoadPage(document: DocHandle, pageIndex: Int): PageHandle?
    fun BitmapCreateExBGRA(address: DataPointer, width: Int, height: Int, stride: Int): PdfiumBitmap?
    fun BitmapDestroy(bitmap: PdfiumBitmap)
    fun RenderPageBitmap(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )

    fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    )

    fun BitmapFillRect(
        bitmap: PdfiumBitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    )

    fun ClosePage(page: PageHandle)
    fun CloseDocument(document: DocHandle)
    fun GetPageWidthF(page: PageHandle): Float
    fun GetPageHeightF(page: PageHandle): Float
    fun DestroyLibrary()

    fun GetLastError(): ULong
}


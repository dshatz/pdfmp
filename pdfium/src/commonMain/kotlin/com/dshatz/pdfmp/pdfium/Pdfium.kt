package com.dshatz.pdfmp.pdfium

interface IPdfium {
    fun InitLibrary()
    fun LoadMemDocument(data: DataPointer, size: Int, password: String?): DocHandle?
    fun LoadDocument(path: String, password: String?): DocHandle?
    fun LoadCustomDocument(access: FileAccess, password: String?): DocHandle?
    fun GetPageCount(document: DocHandle): Int
    fun LoadPage(document: DocHandle, pageIndex: Int): PageHandle?
    fun BitmapCreateExBGRA(
        address: DataPointer,
        width: Int,
        height: Int,
        stride: Int
    ): PdfiumBitmap?

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

expect object Pdfium: IPdfium {
    override fun InitLibrary()
    override fun LoadMemDocument(data: DataPointer, size: Int, password: String?): DocHandle?
    override fun LoadDocument(path: String, password: String?): DocHandle?
    override fun LoadCustomDocument(access: FileAccess, password: String?): DocHandle?
    override fun GetPageCount(document: DocHandle): Int
    override fun LoadPage(document: DocHandle, pageIndex: Int): PageHandle?
    override fun BitmapCreateExBGRA(address: DataPointer, width: Int, height: Int, stride: Int): PdfiumBitmap?
    override fun BitmapDestroy(bitmap: PdfiumBitmap)
    override fun RenderPageBitmap(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )

    override fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    )

    override fun BitmapFillRect(
        bitmap: PdfiumBitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    )

    override fun ClosePage(page: PageHandle)
    override fun CloseDocument(document: DocHandle)
    override fun GetPageWidthF(page: PageHandle): Float
    override fun GetPageHeightF(page: PageHandle): Float
    override fun DestroyLibrary()

    override fun GetLastError(): ULong
}


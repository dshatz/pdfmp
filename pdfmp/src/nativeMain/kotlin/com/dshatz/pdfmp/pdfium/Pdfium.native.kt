package com.dshatz.pdfmp.pdfium

import com.dshatz.internal.pdfium.FPDFBitmap_BGRA
import com.dshatz.internal.pdfium.FPDFBitmap_CreateEx
import com.dshatz.internal.pdfium.FPDFBitmap_Destroy
import com.dshatz.internal.pdfium.FPDFBitmap_FillRect
import com.dshatz.internal.pdfium.FPDF_CloseDocument
import com.dshatz.internal.pdfium.FPDF_ClosePage
import com.dshatz.internal.pdfium.FPDF_DestroyLibrary
import com.dshatz.internal.pdfium.FPDF_GetLastError
import com.dshatz.internal.pdfium.FPDF_GetPageCount
import com.dshatz.internal.pdfium.FPDF_GetPageHeightF
import com.dshatz.internal.pdfium.FPDF_GetPageWidthF
import com.dshatz.internal.pdfium.FPDF_InitLibrary
import com.dshatz.internal.pdfium.FPDF_LoadCustomDocument
import com.dshatz.internal.pdfium.FPDF_LoadDocument
import com.dshatz.internal.pdfium.FPDF_LoadMemDocument
import com.dshatz.internal.pdfium.FPDF_LoadPage
import com.dshatz.internal.pdfium.FPDF_RenderPageBitmap
import com.dshatz.internal.pdfium.FPDF_RenderPageBitmapWithMatrix
import com.dshatz.internal.pdfium.FS_MATRIX
import com.dshatz.internal.pdfium.FS_RECTF
import com.dshatz.pdfmp.PdfTile
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped

actual object Pdfium {
    actual fun InitLibrary() {
        FPDF_InitLibrary()
    }

    actual fun LoadMemDocument(
        data: DataPointer,
        size: Int,
        password: String?
    ): DocHandle? {
        return FPDF_LoadMemDocument(data.ptr, size, password)?.let(::DocHandle)
    }

    actual fun GetPageCount(document: DocHandle): Int {
        return FPDF_GetPageCount(document.ptr)
    }

    actual fun LoadPage(
        document: DocHandle,
        pageIndex: Int
    ): PageHandle? {
        return FPDF_LoadPage(document.ptr, pageIndex)?.let(::PageHandle)
    }

    actual fun RenderPageBitmap(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    ) {
        FPDF_RenderPageBitmap(
            bitmap.bitmap,
            page.ptr,
            startX,
            startY,
            sizeX,
            sizeY,
            rotate,
            flags
        )
    }

    actual fun ClosePage(page: PageHandle) {
        FPDF_ClosePage(page.ptr)
    }

    actual fun CloseDocument(document: DocHandle) {
        FPDF_CloseDocument(document.ptr)
    }

    actual fun DestroyLibrary() {
        FPDF_DestroyLibrary()
    }

    actual fun BitmapCreateExBGRA(
        address: DataPointer,
        width: Int,
        height: Int,
        stride: Int
    ): PdfiumBitmap? {
        return FPDFBitmap_CreateEx(
            width,
            height,
            FPDFBitmap_BGRA,
            address.ptr,
            stride
        )?.let(::PdfiumBitmap)
    }

    actual fun LoadDocument(path: String, password: String?): DocHandle? {
        return FPDF_LoadDocument(path, password)?.let(::DocHandle)
    }

    actual fun LoadCustomDocument(access: FileAccess, password: String?): DocHandle? {
        return FPDF_LoadCustomDocument(access.fileAccessDescriptor.pdfiumAccess, password)
            ?.let(::DocHandle)
    }

    actual fun GetPageWidthF(page: PageHandle): Float {
        return FPDF_GetPageWidthF(page.ptr)
    }

    actual fun GetPageHeightF(page: PageHandle): Float {
        return FPDF_GetPageHeightF(page.ptr)
    }

    actual fun BitmapDestroy(bitmap: PdfiumBitmap) {
        FPDFBitmap_Destroy(bitmap.bitmap)
    }

    actual fun BitmapFillRect(
        bitmap: PdfiumBitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    ) {
        FPDFBitmap_FillRect(
            bitmap.bitmap,
            left,
            top,
            width,
            height,
            color.convert()
        )
    }

    actual fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    ) {
        memScoped {
            val matrix = cValue<FS_MATRIX> {
                this.a = matrix.a
                this.b = matrix.b
                this.c = matrix.c
                this.d = matrix.d
                this.e = matrix.e
                this.f = matrix.f
            }
            val clip = cValue<FS_RECTF> {
                this.left = clip.left
                this.top = clip.top
                this.right = clip.right
                this.bottom = clip.bottom
            }

            FPDF_RenderPageBitmapWithMatrix(
                bitmap.bitmap,
                page.ptr,
                matrix,
                clip,
                flags
            )
        }
    }

    actual fun GetLastError(): ULong {
        return FPDF_GetLastError()
    }
}
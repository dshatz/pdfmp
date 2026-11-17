package com.dshatz.pdfmp.pdfium

import com.dshatz.internal.pdfium.FORMTYPE_NONE
import com.dshatz.internal.pdfium.FPDFBitmap_BGRA
import com.dshatz.internal.pdfium.FPDFDOC_ExitFormFillEnvironment
import com.dshatz.internal.pdfium.FPDFDOC_InitFormFillEnvironment
import com.dshatz.internal.pdfium.FPDF_FFLDraw
import com.dshatz.internal.pdfium.FPDF_FORMFILLINFO
import com.dshatz.internal.pdfium.FPDF_GetFormType
import com.dshatz.internal.pdfium.FPDFBitmap_CreateEx
import com.dshatz.internal.pdfium.FPDFBitmap_Destroy
import com.dshatz.internal.pdfium.FPDF_CloseDocument
import com.dshatz.internal.pdfium.FPDF_ClosePage
import com.dshatz.internal.pdfium.FPDF_DestroyLibrary
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
import com.dshatz.internal.pdfium.pdfmp_FillRect
import com.dshatz.internal.pdfium.pdfmp_GetLastError
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.posix.memset

actual object Pdfium: IPdfium {
    actual override fun InitLibrary() {
        FPDF_InitLibrary()
    }

    actual override fun LoadMemDocument(
        data: DataPointer,
        size: Int,
        password: String?
    ): DocHandle? {
        return FPDF_LoadMemDocument(data.ptr, size, password)?.let(::DocHandle)
    }

    actual override fun GetPageCount(document: DocHandle): Int {
        return FPDF_GetPageCount(document.ptr)
    }

    actual override fun LoadPage(
        document: DocHandle,
        pageIndex: Int
    ): PageHandle? {
        return FPDF_LoadPage(document.ptr, pageIndex)?.let { PageHandle(it, document.form) }
    }

    actual override fun RenderPageBitmap(
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

    actual override fun ClosePage(page: PageHandle) {
        FPDF_ClosePage(page.ptr)
    }

    actual override fun CloseDocument(document: DocHandle) {
        document.form?.let(::FPDFDOC_ExitFormFillEnvironment)
        document.formInfo?.let { nativeHeap.free(it) }
        FPDF_CloseDocument(document.ptr)
    }

    actual override fun InitFormFillEnvironment(document: DocHandle): DocHandle {
        if (document.form != null || FPDF_GetFormType(document.ptr) == FORMTYPE_NONE) return document
        // Zeroed: pdfium calls every callback that is not null. Version 1: no XFA.
        val info = nativeHeap.alloc<FPDF_FORMFILLINFO>()
        memset(info.ptr, 0, sizeOf<FPDF_FORMFILLINFO>().convert())
        info.version = 1
        val form = FPDFDOC_InitFormFillEnvironment(document.ptr, info.ptr)
        if (form == null) {
            nativeHeap.free(info)
            return document
        }
        return DocHandle(document.ptr, form, info)
    }

    actual override fun FFLDraw(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    ) {
        val form = page.form ?: return
        FPDF_FFLDraw(form, bitmap.bitmap, page.ptr, startX, startY, sizeX, sizeY, rotate, flags)
    }

    actual override fun DestroyLibrary() {
        FPDF_DestroyLibrary()
    }

    actual override fun BitmapCreateExBGRA(
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

    actual override fun LoadDocument(path: String, password: String?): DocHandle? {
        return FPDF_LoadDocument(path, password)?.let(::DocHandle)
    }

    actual override fun LoadCustomDocument(access: FileAccess, password: String?): DocHandle? {
        return FPDF_LoadCustomDocument(access.fileAccessDescriptor.pdfiumAccess, password)
            ?.let(::DocHandle)
    }

    actual override fun GetPageWidthF(page: PageHandle): Float {
        return FPDF_GetPageWidthF(page.ptr)
    }

    actual override fun GetPageHeightF(page: PageHandle): Float {
        return FPDF_GetPageHeightF(page.ptr)
    }

    actual override fun BitmapDestroy(bitmap: PdfiumBitmap) {
        FPDFBitmap_Destroy(bitmap.bitmap)
    }

    actual override fun BitmapFillRect(
        bitmap: PdfiumBitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    ) {
        pdfmp_FillRect(
            bitmap.bitmap,
            left,
            top,
            width,
            height,
            color
        )
    }

    actual override fun RenderPageBitmapWithMatrix(
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

    actual override fun GetLastError(): ULong {
        return pdfmp_GetLastError().toULong()
    }
}
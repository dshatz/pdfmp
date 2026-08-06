@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.PdfiumWasm
import com.dshatz.pdfmp.Ptr
import com.dshatz.pdfmp.d
import com.dshatz.pdfmp.setFloat32InWasmMemory
import com.dshatz.pdfmp.wasmPointer
import kotlinx.coroutines.await
import org.jetbrains.skia.impl.NativePointer
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

actual object Pdfium {
    actual fun InitLibrary() {
        PdfiumWasm._FPDF_InitLibrary()
    }

    actual fun LoadMemDocument(
        data: DataPointer,
        size: Int,
        password: String?
    ): DocHandle? {
        val ptr = PdfiumWasm._FPDF_LoadMemDocument(
            data.ptr,
            size,
            null // todo
        ).wasmPointer()
        return ptr?.let(::DocHandle)
    }

    actual fun LoadDocument(
        path: String,
        password: String?
    ): DocHandle? {
        val ptr = PdfiumWasm._FPDF_LoadDocument(0u, null) // todo
        return DocHandle(Ptr(ptr))
    }

    actual fun LoadCustomDocument(
        access: FileAccess,
        password: String?
    ): DocHandle? {
        TODO("Not yet implemented")
    }

    actual fun GetPageCount(document: DocHandle): Int {
        return PdfiumWasm._FPDF_GetPageCount(document.ptr.ptr)
    }

    actual fun LoadPage(
        document: DocHandle,
        pageIndex: Int
    ): PageHandle? {
        val page = PdfiumWasm._FPDF_LoadPage(document.ptr.ptr, pageIndex).wasmPointer()
        return page?.let(::PageHandle)
    }

    actual fun BitmapCreateExBGRA(
        address: DataPointer,
        width: Int,
        height: Int,
        stride: Int
    ): PdfiumBitmap? {
        val bytes = stride * height
        val pdfiumPtr = PdfiumWasm._malloc(bytes)
        val bitmap = PdfiumWasm._FPDFBitmap_CreateEx(
            width,
            height,
            4,
            pdfiumPtr,
            stride
        ).wasmPointer()
        return bitmap?.let {
            PdfiumBitmap(
                Ptr(pdfiumPtr),
                bytes
            )
        }
    }

    actual fun BitmapDestroy(bitmap: PdfiumBitmap) {
        PdfiumWasm._FPDFBitmap_Destroy(bitmap.pdfiumPtr.ptr)
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
        PdfiumWasm._FPDF_RenderPageBitmap(
            bitmap.pdfiumPtr.ptr,
            page.ptr.ptr,
            startX,
            startY,
            sizeX,
            sizeY,
            rotate,
            flags
        )
    }

    actual fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    ) {

        val matrixPtr = PdfiumWasm._malloc(24)  // 6 * 4 bytes
        val rectPtr = PdfiumWasm._malloc(16) // 4 * 4 bytes
        try {
            val matrixOffset = matrixPtr / 4u
            setFloat32InWasmMemory(matrixOffset, matrix.a)
            setFloat32InWasmMemory(matrixOffset + 1u, matrix.b)
            setFloat32InWasmMemory(matrixOffset + 2u, matrix.c)
            setFloat32InWasmMemory(matrixOffset + 3u, matrix.d)
            setFloat32InWasmMemory(matrixOffset + 4u, matrix.e)
            setFloat32InWasmMemory(matrixOffset + 5u, matrix.f)

            val rectOffset = rectPtr / 4u
            setFloat32InWasmMemory(rectOffset + 0u, clip.left)
            setFloat32InWasmMemory(rectOffset + 1u, clip.top)
            setFloat32InWasmMemory(rectOffset + 2u, clip.right)
            setFloat32InWasmMemory(rectOffset + 3u, clip.bottom)
            PdfiumWasm._FPDF_RenderPageBitmapWithMatrix(
                bitmap.pdfiumPtr.ptr,
                page.ptr.ptr,
                matrixPtr,
                rectPtr,
                0
            )
        } finally {
            PdfiumWasm._free(matrixPtr)
            PdfiumWasm._free(rectPtr)
        }
    }

    actual fun BitmapFillRect(
        bitmap: PdfiumBitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: UInt
    ) {
        d("Bitmap: ${bitmap.pdfiumPtr.ptr}, width = $width, height = $height, left = $left, top = $top")
        PdfiumWasm._FPDFBitmap_FillRect(
            bitmap.pdfiumPtr.ptr,
            left,
            top,
            width,
            height,
            color
        )
    }

    actual fun ClosePage(page: PageHandle) {
        PdfiumWasm._FPDF_ClosePage(page.ptr.ptr)
    }

    actual fun CloseDocument(document: DocHandle) {
        PdfiumWasm._FPDF_CloseDocument(document.ptr.ptr)
    }

    actual fun GetPageWidthF(page: PageHandle): Float {
        return PdfiumWasm._FPDF_GetPageWidthF(page.ptr.ptr)
    }

    actual fun GetPageHeightF(page: PageHandle): Float {
        return PdfiumWasm._FPDF_GetPageHeightF(page.ptr.ptr)
    }

    actual fun DestroyLibrary() {
        PdfiumWasm._FPDF_DestroyLibrary()
    }

    actual fun GetLastError(): ULong {
        return PdfiumWasm._FPDF_GetLastError()
    }
}


@Suppress("INVISIBLE_REFERENCE")
internal suspend fun awaitSkiko(): JsAny = org.jetbrains.skiko.wasm.awaitSkiko.await()!!

private fun skikoMemory(skikoWasm: JsAny): ArrayBuffer =
    js("skikoWasm.wasmExports.memory.buffer")

private fun ArrayBuffer.set(data: ArrayBuffer, offset: NativePointer) {
    Int8Array(this).set(Int8Array(data), offset)
}
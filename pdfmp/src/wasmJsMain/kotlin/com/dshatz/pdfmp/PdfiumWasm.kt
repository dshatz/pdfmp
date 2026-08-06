package com.dshatz.pdfmp

import kotlin.js.Promise


@OptIn(ExperimentalWasmJsInterop::class)
@JsName("Module")
external object PdfiumWasm {
    fun _FPDF_InitLibrary()
    fun _FPDF_LoadMemDocument(data: UInt, size: Int, password: Int?): UInt
    fun _FPDF_LoadDocument(path: UInt, password: UInt?): UInt
    fun _FPDF_GetPageCount(document: UInt): Int
    fun _FPDF_GetPageWidthF(page: UInt): Float
    fun _FPDF_GetPageHeightF(page: UInt): Float
    fun _FPDF_GetLastError(): ULong
    fun _FPDFBitmap_CreateEx(width: Int, height: Int, format: Int, address: UInt, stride: Int): UInt
    fun _FPDFBitmap_FillRect(bitmap: UInt, left: Int, top: Int, width: Int, height: Int, color: UInt)
    fun _FPDFBitmap_Destroy(bitmap: UInt)
    fun _FPDF_LoadPage(document: UInt, pageIndex: Int): UInt
    fun _FPDF_RenderPageBitmap(
        bitmap: UInt,
        page: UInt,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    )

    fun _FPDF_RenderPageBitmapWithMatrix(bitmap: UInt, page: UInt, matrix: UInt, rect: UInt, flags: Int)
    fun _FPDF_ClosePage(page: UInt)
    fun _FPDF_CloseDocument(document: UInt)
    fun _FPDF_DestroyLibrary()

    fun _malloc(size: Int): UInt
    fun _free(ptr: UInt)
}

@OptIn(ExperimentalWasmJsInterop::class)
fun setFloat32InWasmMemory(index: UInt, value: Float) {
    js("""
        Module.HEAPF32[index] = value;
    """)
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(ptr, byte) => { Module.HEAP8[ptr] = byte; }")
external fun writeWasmByte(ptr: UInt, byte: Byte)


@OptIn(ExperimentalWasmJsInterop::class)
@JsName("awaitPdfium")
external val awaitPdfium: Promise<JsAny?> // or standard promise binding
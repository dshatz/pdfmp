@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.PdfiumWasm
import com.dshatz.pdfmp.wasmPointer
import org.khronos.webgl.Int8Array

actual class ProgressiveDocument actual constructor(length: Int, chunkSize: Int) {
    actual val chunks = ProgressiveChunks(length, chunkSize)

    // Its address identifies the document in the callbacks.
    private val data: UInt = PdfiumWasm._malloc(length)

    // FX_FILEAVAIL { int version; IsDataAvail }, 4 bytes per field, followed by [data] for the callback.
    private val fileAvail: UInt = PdfiumWasm._malloc(12).also {
        writeInt32(it, 1)
        writeInt32(it + 4u, WebCallbacks.isDataAvail)
        writeInt32(it + 8u, data.toInt())
    }

    // FPDF_FILEACCESS { unsigned long m_FileLen; m_GetBlock; void* m_Param = data }.
    private val fileAccess: UInt = PdfiumWasm._malloc(12).also {
        writeInt32(it, length)
        writeInt32(it + 4u, WebCallbacks.getBlock)
        writeInt32(it + 8u, data.toInt())
    }

    private val avail: UInt

    init {
        ProgressiveCallbacks.register(data.toLong(), chunks)
        avail = PdfiumWasm._FPDFAvail_Create(fileAvail, fileAccess)
    }

    /** Stores [bytes] starting at chunk [firstChunk]. */
    fun addChunks(firstChunk: Int, bytes: Int8Array) {
        val offset = firstChunk * chunks.chunkSize
        writeToPdfium(data + offset.toUInt(), bytes)
        chunks.markReceived(offset, bytes.length)
    }

    actual fun isDocAvail(): Availability =
        chunks.check { PdfiumWasm._FPDFAvail_IsDocAvail(avail, WebCallbacks.hints) }

    actual fun isPageAvail(pageIndex: Int): Availability =
        chunks.check { PdfiumWasm._FPDFAvail_IsPageAvail(avail, pageIndex, WebCallbacks.hints) }

    actual fun document(): DocHandle? = PdfiumWasm._FPDFAvail_GetDocument(avail, null).wasmPointer()?.let(::DocHandle)

    actual fun destroy() {
        PdfiumWasm._FPDFAvail_Destroy(avail)
        ProgressiveCallbacks.unregister(data.toLong())
        PdfiumWasm._free(fileAccess)
        PdfiumWasm._free(fileAvail)
        PdfiumWasm._free(data)
    }

    private object WebCallbacks {
        val isDataAvail: Int = addFunctionIIIReturningI { fileAvail, offset, size ->
            val data = readInt32(fileAvail + 8)
            if (ProgressiveCallbacks.isDataAvail(data.unsigned(), offset.unsigned(), size.unsigned())) 1 else 0
        }

        val getBlock: Int = addFunctionIIIIReturningI { data, position, buffer, size ->
            val read = ProgressiveCallbacks.getBlock(data.unsigned(), position.unsigned(), size.unsigned()) {
                copyWithinPdfium(buffer, data + position, size)
            }
            if (read) 1 else 0
        }

        // FX_DOWNLOADHINTS { int version; AddSegment }.
        val hints: UInt = PdfiumWasm._malloc(8).also {
            writeInt32(it, 1)
            writeInt32(it + 4u, addFunctionIIIReturningUnit { _, offset, size ->
                ProgressiveCallbacks.addSegment(offset.unsigned(), size.unsigned())
            })
        }
    }
}

/** Pointers and sizes arrive as signed 32-bit numbers. */
private fun Int.unsigned(): Long = toUInt().toLong()

private fun writeInt32(ptr: UInt, value: Int) = writeInt32(ptr.toInt(), value)

@JsFun("(ptr, value) => { Module.HEAP32[ptr >>> 2] = value; }")
private external fun writeInt32(ptr: Int, value: Int)

@JsFun("(ptr) => Module.HEAP32[ptr >>> 2]")
private external fun readInt32(ptr: Int): Int

@JsFun("(dst, src, size) => { Module.HEAPU8.copyWithin(dst >>> 0, src >>> 0, (src >>> 0) + (size >>> 0)); }")
private external fun copyWithinPdfium(destination: Int, source: Int, size: Int)

private fun writeToPdfium(ptr: UInt, bytes: Int8Array) = writeToPdfium(ptr.toInt(), bytes)

@JsFun("(ptr, bytes) => { Module.HEAP8.set(bytes, ptr >>> 0); }")
private external fun writeToPdfium(ptr: Int, bytes: Int8Array)

@JsFun("(f) => Module.addFunction((a, b, c) => f(a, b, c), 'iiii')")
private external fun addFunctionIIIReturningI(f: (Int, Int, Int) -> Int): Int

@JsFun("(f) => Module.addFunction((a, b, c, d) => f(a, b, c, d), 'iiiii')")
private external fun addFunctionIIIIReturningI(f: (Int, Int, Int, Int) -> Int): Int

@JsFun("(f) => Module.addFunction((a, b, c) => { f(a, b, c); }, 'viii')")
private external fun addFunctionIIIReturningUnit(f: (Int, Int, Int) -> Unit): Int

package com.dshatz.pdfmp.pdfium

import com.dshatz.jsworker.JSWorker
import com.dshatz.jsworker.TransferOwnership
import com.dshatz.pdfmp.Ptr
import com.dshatz.pdfmp.pdfium.Pdfium.free
import com.dshatz.pdfmp.writeWasmByte
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.io.encoding.Base64
import kotlin.js.unsafeCast

@JSWorker("pdfium-wasm.js", "pdfium.js")
class PdfiumWebWorker: IPdfium by Pdfium {

    fun transferBytes(@TransferOwnership data: Int8Array): Ptr {
        return runCatching {
            println("Transferring byte array of size ${data.byteLength} to Pdfium memory.")
            val dataPtr = Pdfium.malloc(data.byteLength)

            writeArray(dataPtr.ptr, data)
            dataPtr
        }.recover {
            it.printStackTrace()
            Ptr(0u)
        }.getOrThrow()
    }

    fun releaseDocumentBytes(ptr: Ptr) {
        ptr.free()
    }


    @OptIn(ExperimentalWasmJsInterop::class)
    @TransferOwnership
    fun bitmapAsArrayBuffer(@TransferOwnership buffer: ArrayBuffer, bitmap: PdfiumBitmap): ArrayBuffer {
        copyPdfiumToBuffer(bitmap.memoryPtr.ptr, buffer, bitmap.bytes)
        return buffer.unsafeCast()
    }
}

@JsFun("(size) => new ArrayBuffer(size)")
private external fun makeBuffer(size: Int): JsAny

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(ptr, bytes) => { Module.HEAP8.set(bytes, ptr); }")
private external fun writeArray(ptr: UInt, bytes: Int8Array)

@OptIn(ExperimentalWasmJsInterop::class)
private fun pdfiumMemory(): JsAny = js("Module.HEAP8.buffer")

@JsFun("""
    (srcPtr, dst, size) => {
        let pdfiumData = new Int8Array(Module.HEAP8.buffer, srcPtr, size);
        let target = new Int8Array(dst, 0, size);
        target.set(pdfiumData);
    }
""")
private external fun copyPdfiumToBuffer(srcPtr: UInt, dst: JsAny, size: Int)
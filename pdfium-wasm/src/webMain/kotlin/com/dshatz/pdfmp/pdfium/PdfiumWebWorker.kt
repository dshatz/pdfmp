package com.dshatz.pdfmp.pdfium

import com.dshatz.jsworker.JSWorker
import com.dshatz.jsworker.JSWorkerIgnore
import com.dshatz.jsworker.TransferOwnership
import com.dshatz.pdfmp.Ptr
import com.dshatz.pdfmp.pdfium.Pdfium.free
import com.dshatz.pdfmp.writeWasmByte
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.io.encoding.Base64

@JSWorker("pdfium-wasm.js", "pdfium.js")
class PdfiumWebWorker: IPdfium by Pdfium {

    // Debug counters, kept in the worker because a bitmap created for a cancelled caller is never seen by the main thread.
    private var liveBitmaps = 0
    private var liveBitmapBytes = 0L
    private var openPages = 0

    override fun BitmapCreateExBGRA(address: DataPointer, width: Int, height: Int, stride: Int): PdfiumBitmap? {
        return Pdfium.BitmapCreateExBGRA(address, width, height, stride)?.also {
            liveBitmaps++
            liveBitmapBytes += stride.toLong() * height
            logStats("bitmap created")
        }
    }

    override fun BitmapDestroy(bitmap: PdfiumBitmap) {
        Pdfium.BitmapDestroy(bitmap)
        liveBitmaps--
        liveBitmapBytes -= bitmap.bytes
        logStats("bitmap destroyed")
    }

    override fun LoadPage(document: DocHandle, pageIndex: Int): PageHandle? {
        return Pdfium.LoadPage(document, pageIndex)?.also {
            openPages++
            logStats("page $pageIndex loaded")
        }
    }

    override fun ClosePage(page: PageHandle) {
        Pdfium.ClosePage(page)
        openPages--
        logStats("page closed")
    }

    @JSWorkerIgnore
    private fun logStats(event: String) {
        if (!LOG_STATS) return
        println("[PdfiumStats] $event: bitmaps = $liveBitmaps (${liveBitmapBytes / (1024 * 1024)} MB), open pages = $openPages")
    }

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


    /**
     * Copies the bitmap pixels out of pdfium memory into a fresh buffer that is transferred to the caller.
     * No buffer is kept around on either side; the caller's copy is garbage collected once consumed.
     */
    @TransferOwnership
    fun bitmapAsArrayBuffer(bitmap: PdfiumBitmap): ArrayBuffer {
        return copyOutOfPdfium(bitmap.memoryPtr.ptr, bitmap.bytes)
    }
}

/** Logs every bitmap and page open/close. Console output is slow, so keep this off unless hunting leaks. */
private const val LOG_STATS = false

@JsFun("(size) => new ArrayBuffer(size)")
private external fun makeBuffer(size: Int): JsAny

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(ptr, bytes) => { Module.HEAP8.set(bytes, ptr); }")
private external fun writeArray(ptr: UInt, bytes: Int8Array)

@OptIn(ExperimentalWasmJsInterop::class)
private fun pdfiumMemory(): JsAny = js("Module.HEAP8.buffer")

// slice() copies into a new standalone ArrayBuffer, so transferring it leaves pdfium memory intact.
@JsFun("(srcPtr, size) => Module.HEAP8.slice(srcPtr, srcPtr + size).buffer")
private external fun copyOutOfPdfium(srcPtr: UInt, size: Int): ArrayBuffer
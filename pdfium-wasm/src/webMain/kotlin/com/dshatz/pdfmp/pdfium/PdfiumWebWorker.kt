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


    // Coarse operations: a whole render is one message, instead of one per pdfium call.

    /** [PdfiumRenderCore.renderTile] into a new [tileWidth] x [tileHeight] bitmap; returns its pixels. */
    @TransferOwnership
    fun renderTile(
        page: PageHandle,
        x: Int,
        y: Int,
        tileWidth: Int,
        tileHeight: Int,
        scaledPageWidth: Int,
        scaledPageHeight: Int,
        frameColor: UInt?,
    ): ArrayBuffer = renderToBuffer(tileWidth, tileHeight) { bitmap ->
        PdfiumRenderCore.renderTile(
            this, bitmap, page, x, y, tileWidth, tileHeight, scaledPageWidth, scaledPageHeight, frameColor
        )
    }

    /**
     * [PdfiumRenderCore.renderPage] at [width] x [height] into a new [bitmapWidth] x [bitmapHeight] bitmap; returns
     * its pixels. The bitmap matches the caller's buffer, whose size can differ from the page size.
     */
    @TransferOwnership
    fun renderPage(page: PageHandle, bitmapWidth: Int, bitmapHeight: Int, width: Int, height: Int): ArrayBuffer =
        renderToBuffer(bitmapWidth, bitmapHeight) { bitmap ->
            PdfiumRenderCore.renderPage(this, bitmap, page, width, height)
        }

    fun pageRatio(page: PageHandle): Float = PdfiumRenderCore.pageRatio(this, page)

    /**
     * Runs [block] on a temporary bitmap and copies its pixels into a fresh buffer that is transferred to the caller.
     * Everything happens within one call, so the bitmap is always destroyed, even if the caller was cancelled.
     */
    @JSWorkerIgnore
    private fun renderToBuffer(width: Int, height: Int, block: (PdfiumBitmap) -> Unit): ArrayBuffer {
        val bitmap = BitmapCreateExBGRA(DataPointer(0u), width, height, width * 4)
            ?: error("Could not create Pdfium bitmap")
        try {
            block(bitmap)
            return copyOutOfPdfium(bitmap.memoryPtr.ptr, bitmap.bytes)
        } finally {
            BitmapDestroy(bitmap)
        }
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
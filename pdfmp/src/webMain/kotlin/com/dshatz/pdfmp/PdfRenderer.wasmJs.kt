@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import com.dshatz.jsworker.workerScope
import com.dshatz.kni.annotations.JniCall
import com.dshatz.kni.flows.NativeBackedFlow
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.imagebuffer.bytes
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.PdfClip
import com.dshatz.pdfmp.pdfium.PdfMatrix
import com.dshatz.pdfmp.pdfium.PdfiumBitmap
import com.dshatz.pdfmp.pdfium.PdfiumWebWorkerWebWorker
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.Version.skiko
import org.jetbrains.skiko.wasm.awaitSkiko
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toInt8Array

@OptIn(ExperimentalWasmJsInterop::class, InternalSkikoApi::class)
actual class PdfRenderer: PdfiumRenderer {
    val worker = PdfiumWebWorkerWebWorker()
    private val source: PdfSource?
    private val customSource: CustomPdfSourceAdapter?

    actual constructor(source: CustomPdfSourceAdapter) {
        this.customSource = source
        this.source = null
    }

    actual constructor(source: PdfSource) {
        this.source = source
        this.customSource = null
    }

    private val skikoWasm: CompletableDeferred<JsAny> = CompletableDeferred()

    init {
        GlobalScope.launch {
            worker.InitLibrary()
            awaitSkiko {
                skikoWasm.complete(it)
            }
        }
    }

    suspend fun pdfium() = worker

    private var sourceBytesPtr: UInt? = null

    actual override suspend fun openDocument(): Result<Unit> {
        d("openDocument running!")
        return runCatching {
            if (source != null) {
                when (source) {
                    is PdfSource.PdfBytes -> {
                        val workerDataPtr = pdfium().transferBytes(source.bytes.toInt8Array())
                        val doc = pdfium().LoadMemDocument(DataPointer(workerDataPtr.ptr), source.bytes.size, null) ?: error("LoadMemDocument returned null")

                        this.doc = doc
                        this.sourceBytesPtr = workerDataPtr.ptr
                    }
                    is PdfSource.PdfPath -> TODO()
                }
            }
        }
    }

    @JniCall
    actual override suspend fun getPageCount(): Int {
        return pdfium().GetPageCount(doc)
    }

    override suspend fun PageHandle.getPageHeight(): Float {
        return pdfium().GetPageHeightF(this)
    }

    override suspend fun PageHandle.getPageWidth(): Float {
        return pdfium().GetPageWidthF(this)
    }

    private val bufferPool = mutableListOf<ArrayBuffer>()
    private val mutex = Mutex()
    private suspend fun getBuffer(size: Int): ArrayBuffer {
        val existing = mutex.withLock { bufferPool.firstOrNull { it.byteLength >= size }?.apply { bufferPool.remove(this) } }
        return if (existing != null) {
            existing
        } else {
            val b = ArrayBuffer(size)
            b
        }
    }
    private suspend fun returnBuffer(buffer: ArrayBuffer) = mutex.withLock {
        bufferPool += buffer
    }

    override suspend fun <R> ImageBuffer.withFpdfBitmap(block: suspend (bitmap: PdfiumBitmap) -> R) {
        d("withFpdfBitmap")
        val bitmap = pdfium().BitmapCreateExBGRA(
            DataPointer(0u), // On wasm it will allocate memory itself.
            width,
            height,
            stride
        ) ?: error("Could not create Pdfium bitmap")
        val size = stride * height
        val pixelBuffer = try {
            block(bitmap)
            val buffer = getBuffer(size)
            val pixelBuffer = pdfium().bitmapAsArrayBuffer(buffer, bitmap)
            pixelBuffer
        } finally {
            workerScope.launch {
                bitmap.destroy()
            }
        }
        try {
            val pixels = Int8Array(pixelBuffer, 0, bitmap.bytes)
            val skiko = skikoWasm.await()
            copyDirectToSkiko(pixels, skikoMemoryBuffer(skiko), data.writableData().toUInt())
        } finally {
            returnBuffer(pixelBuffer)
        }
    }

    override suspend fun PdfiumBitmap.destroy() {
        pdfium().BitmapDestroy(this)
    }

    override suspend fun PdfiumBitmap.fillRect(left: Int, top: Int, width: Int, height: Int, color: UInt) {
        pdfium().BitmapFillRect(this, left, top, width, height, color)
    }


    override suspend fun RenderPageBitmap(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int
    ) {
        pdfium().RenderPageBitmap(
            bitmap,
            page,
            startX,
            startY,
            sizeX,
            sizeY,
            rotate,
            flags
        )
    }

    override suspend fun RenderPageBitmapWithMatrix(
        bitmap: PdfiumBitmap,
        page: PageHandle,
        matrix: PdfMatrix,
        clip: PdfClip,
        flags: Int
    ) {
        pdfium().RenderPageBitmapWithMatrix(bitmap, page, matrix, clip, flags)
    }

    override fun PageHandle.closePage() {
        workerScope.launch {
            pdfium().ClosePage(this@closePage)
        }
    }

    override suspend fun DocHandle.openPage(pageIndex: Int): PageHandle? {
        return pdfium().LoadPage(doc, pageIndex)
    }


    actual override fun close() {
        GlobalScope.launch {
            if (isDocLoaded()) {
                pdfium().CloseDocument(doc)
            }
            sourceBytesPtr?.let(PdfiumWasm::_free)
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class, InternalSkikoApi::class)
fun awaitSkiko(callback: (JsAny) -> Unit) = awaitSkiko.then {
    callback(it)
    Unit.toJsReference()
}

fun copyDirectToSkiko(src: Int8Array, skikoMemory: ArrayBuffer, skikoPtr: UInt) {
    js("""(function(src, skikoMemory, skikoPtr) {
        const dest = new Uint8Array(skikoMemory, skikoPtr, src.length);
        dest.set(src);
    })(src, skikoMemory, skikoPtr)""")
}

@JsFun("(skiko) => skiko.wasmExports.memory.buffer")
private external fun skikoMemoryBuffer(skiko: JsAny): ArrayBuffer

@JsFun("(buf) => buf.transfer(0)")
private external fun releaseBuffer(buf: ArrayBuffer)
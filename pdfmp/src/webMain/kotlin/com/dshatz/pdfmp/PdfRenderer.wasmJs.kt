@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.kni.buffers.allocateBuffer
import com.dshatz.kni.flows.NativeBackedFlow
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.imagebuffer.bytes
import com.dshatz.pdfmp.pdfium.Availability
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.PdfiumWebWorkerWebWorker
import com.dshatz.pdfmp.pdfium.ProgressiveDocHandle
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        return runCatching {
            if (source != null) {
                when (source) {
                    is PdfSource.PdfBytes -> {
                        val workerDataPtr = pdfium().transferBytes(source.bytes.toInt8Array())
                        val doc = pdfium().LoadMemDocument(DataPointer(workerDataPtr.ptr), source.bytes.size, null) ?: error("LoadMemDocument returned null")

                        this.doc = doc
                        this.sourceBytesPtr = workerDataPtr.ptr
                    }
                    is PdfSource.PdfPath -> throw UnsupportedOperationException("File paths are not supported on the web, use PdfData.url or PdfData.fromBlob")
                }
            } else if (customSource != null) {
                openProgressive(customSource)
            }
        }
    }

    private var progressive: ProgressiveDocHandle? = null

    override suspend fun progressiveOpen(length: Int, chunkSize: Int) {
        progressive = pdfium().openProgressive(length, chunkSize)
    }

    override suspend fun progressiveIsDocAvail(): Availability = pdfium().isDocAvail(progressive!!)

    override suspend fun progressiveIsPageAvail(pageIndex: Int): Availability =
        pdfium().isPageAvail(progressive!!, pageIndex)

    override suspend fun progressiveDocument(): DocHandle? = pdfium().progressiveDocument(progressive!!)

    override suspend fun progressiveFill(firstChunk: Int, size: Int, read: suspend (ByteBuffer) -> Unit) {
        val buffer = allocateBuffer(size.toLong())
        read(buffer)
        pdfium().addChunks(progressive!!, firstChunk, buffer.byteArray.toInt8Array())
    }

    @JniCall
    actual override suspend fun getPageCount(): Int {
        return pdfium().GetPageCount(doc)
    }

    override suspend fun renderTileInto(buffer: ImageBuffer, page: PageHandle, tile: PdfTile, frameColor: UInt?) {
        val pixels = pdfium().renderTile(
            page,
            tile.key.x,
            tile.key.y,
            buffer.width,
            buffer.height,
            tile.scaledPage.width,
            tile.scaledPage.height,
            frameColor
        )
        buffer.copyFrom(pixels)
    }

    override suspend fun renderPageInto(buffer: ImageBuffer, page: PageHandle, dimensions: PageDimensions) {
        val pixels = pdfium().renderPage(page, buffer.width, buffer.height, dimensions.width, dimensions.height)
        buffer.copyFrom(pixels)
    }

    override suspend fun pageRatio(page: PageHandle): Float = pdfium().pageRatio(page)

    private suspend fun ImageBuffer.copyFrom(pixels: ArrayBuffer) {
        val skiko = skikoWasm.await()
        // The caller frees this ImageBuffer as soon as it is cancelled (e.g. scrolled away), after which it may be
        // reused or closed. The copy below has no suspension point, so checking here guarantees we never write
        // into a buffer that no longer belongs to this render.
        currentCoroutineContext().ensureActive()
        copyDirectToSkiko(Int8Array(pixels), skikoMemoryBuffer(skiko), data.writableData().toUInt())
        // Detaching frees the pixel copy right away instead of leaving it for the next GC.
        releaseBuffer(pixels)
    }

    override fun PageHandle.closePage() {
        GlobalScope.launch {
            pdfium().ClosePage(this@closePage)
        }
    }

    override suspend fun DocHandle.openPage(pageIndex: Int): PageHandle? {
        // NonCancellable: the page must reach pageHandleCache even if the caller was cancelled, or it is never closed.
        return withContext(NonCancellable) {
            pdfium().LoadPage(doc, pageIndex)
        }
    }


    actual override fun close() {
        GlobalScope.launch {
            if (isDocLoaded()) {
                pageHandleCache.forEach {
                    it.value.closePage()
                }
                pageHandleCache.clear()
                pdfium().CloseDocument(doc)
            }
            sourceBytesPtr?.let {
                pdfium().releaseDocumentBytes(Ptr(it))
            }
            progressive?.let { pdfium().closeProgressive(it) }
            customSource?.close()
            worker.terminate()
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

@JsFun("(buf) => { buf.transfer(0); }")
private external fun releaseBuffer(buf: ArrayBuffer)
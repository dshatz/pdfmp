package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.CustomSourceDescriptor
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.Buffer
import kotlinx.io.readFloat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PdfRenderer(private val renderer: PdfRendererPtr) {
    
    actual suspend fun render(renderRequest: RenderRequest): Result<RenderResponse> = suspendCancellableCoroutine { cont ->
        val packed = renderRequest.pack()
        val callback = object : RenderCallback {
            override fun onSuccess(result: ByteArray) {
                val buffer = Buffer()
                buffer.write(result)
                cont.resume(Result.success(RenderResponse.unpack(buffer)))
            }

            override fun onFailure(message: String) {
                cont.resume(Result.failure(IllegalStateException(message)))
            }

            override fun close() {}

        }
        PDFBridge.renderAsync(renderer, packed, callback)
        /*return runCatching {
            val packed = renderRequest.pack()
            val response = unpackResult(
                PDFBridge.render(renderer,packed),
                RenderResponse::unpack
            )
            response.getOrThrow()
        }*/
    }

    actual fun close() {
        PDFBridge.close(renderer)
    }

    actual fun getPageCount(): Result<Int> {
        return runCatching {
            unpackResult(PDFBridge.getPageCount(renderer), Buffer::readInt).getOrThrow()
        }
    }

    actual fun getPageRatio(pageIndex: Int): Result<Float> {
        return runCatching {
            unpackResult(PDFBridge.getAspectRatio(renderer, pageIndex), { readFloat() }).getOrThrow()
        }
    }
}

actual object PdfRendererFactory {
    actual fun createFromSource(
        source: PdfSource,
    ): Result<PdfRenderer> {
        return when (source) {
            is PdfSource.Basic -> {
                val nativePtr: Result<PdfRendererPtr> = unpackResult(PDFBridge.createNativeRenderer(source.pack()), Buffer::readLong)
                nativePtr.map { PdfRenderer(it) }
            }
            is PdfSource.Custom -> {
                val nativePtr = unpackResult(PDFBridge.createNativeRendererCustom(source.customSourceDescriptor.jvmCustomSource), Buffer::readLong)
                nativePtr.map { PdfRenderer(it) }
            }
        }
    }
}
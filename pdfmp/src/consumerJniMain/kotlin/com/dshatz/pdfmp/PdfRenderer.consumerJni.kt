package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.io.Buffer

actual class PdfRenderer(private val renderer: PdfRendererPtr) {
    
    actual fun render(renderRequest: RenderRequest): Result<RenderResponse> {
        return runCatching {
            val packed = renderRequest.pack()
            val response = unpackResult(
                PDFBridge.render(renderer,packed),
                RenderResponse::unpack
            )
            response.getOrThrow()
        }
    }

    actual fun close() {
        PDFBridge.close(renderer)
    }

    actual fun getPageCount(): Result<Int> {
        return runCatching {
            unpackResult(PDFBridge.getPageCount(renderer), Buffer::readInt).getOrThrow()
        }
    }

    actual fun getPageRatios(): Result<List<Float>> {
        return runCatching {
            unpackResult(PDFBridge.getPageRatios(renderer), ::unpackFloats).getOrThrow()
        }
    }
}

actual object PdfRendererFactory {
    actual fun createFromSource(
        source: PdfSource,
    ): Result<PdfRenderer> {
        val nativePtr: Result<PdfRendererPtr> = unpackResult(PDFBridge.createNativeRenderer(source.pack()), Buffer::readLong)
        return nativePtr.map { PdfRenderer(it) }
    }
}
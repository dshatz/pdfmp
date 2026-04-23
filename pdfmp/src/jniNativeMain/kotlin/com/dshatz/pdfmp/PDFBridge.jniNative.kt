package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.model.RenderRequest
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer

/*
actual object PDFBridge {
    @JniCall
    actual fun renderAsync(
        renderer: PdfRendererPtr,
        request: RenderRequest,
        callback: RenderCallback
    ) {
        val renderer = renderer.getRenderer()
        renderer.renderAsync(request, callback)
    }

    @JniCall
    actual fun getPageCount(renderer: PdfRendererPtr): Result<Int> {
        return renderer.getRenderer().getPageCount()
    }

    @JniCall
    actual fun getPageRatio(
        renderer: PdfRendererPtr,
        pageIndex: Int
    ): kotlin.Result<Float> {
        return renderer.getRenderer().getPageRatio(pageIndex)
    }

    @JniCall
    actual fun close(renderer: PdfRendererPtr,) {
        renderer.getRenderer().close()
    }

    private fun PdfRendererPtr.getRenderer(): PdfRenderer {
        return runCatching {
            val rendererRef = toCPointer<COpaque>()!!.asStableRef<PdfRenderer>()
            rendererRef.get()
        }.getOrElse {
            e("Could not get renderer", it)
            error("")
        }
    }

    @JniCall
    actual fun initNative() {
    }
}*/

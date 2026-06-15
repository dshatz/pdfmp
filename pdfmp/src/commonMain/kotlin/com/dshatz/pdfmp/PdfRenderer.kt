package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

expect class PdfRenderer: AutoCloseable {

    constructor(source: CustomPdfSourceAdapter)
    constructor(source: PdfSource)

    @JniCall
    fun openDocument(): Result<Unit>

    @JniCall
    fun renderAsync(renderRequest: RenderRequest, callback: RenderCallback)

    @JniCall
    fun getPageCount(): Result<Int>
    @JniCall
    fun getPageRatio(pageIndex: Int): Result<Float>

    override fun close()
}

suspend fun PdfRenderer.renderSuspend(renderRequest: RenderRequest): Result<RenderResponse> = suspendCoroutine { cont ->
    renderAsync(renderRequest, object: RenderCallback {
        override fun onSuccess(result: RenderResponse) {
            cont.resume(Result.success(result))
        }

        override fun onFailure(message: String) {
            cont.resumeWithException(RuntimeException(message))
        }

        override fun close() {}
    })
}

suspend fun PdfRenderer.render(renderRequest: RenderRequest): Result<RenderResponse> = suspendCoroutine {
    renderAsync(renderRequest, object : RenderCallback {
        override fun onSuccess(result: RenderResponse) {
            it.resume(Result.success(result))
        }

        override fun onFailure(message: String) {
            it.resume(Result.failure(RuntimeException("Pdf render failed: $message")))
        }
        override fun close() {}
    })
}

/*
expect object PdfRendererFactory {
    fun createFromSource(source: PdfSource): Result<PdfRenderer>
    fun createFromCustomSource(source: CustomPdfSourceAdapter): Result<PdfRenderer>
}
*/

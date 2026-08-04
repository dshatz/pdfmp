package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

expect class PdfRenderer: AutoCloseable {

    constructor(source: CustomPdfSourceAdapter)
    constructor(source: PdfSource)

    @JniCall
    fun openDocument(): Result<Unit>

    @JniCall
    fun renderAsync(renderRequest: RenderRequest, callback: RenderCallback)

    @JniCall
    fun renderTileAsync(tile: PdfTile, bufferInfo: BufferInfo, callback: TileRenderCallback)

    @JniCall
    fun getPageCount(callback: PdfOperationCallback)
    @JniCall
    fun getPageRatio(pageIndex: Int, callback: PdfOperationCallback)

    override fun close()
}

suspend fun PdfRenderer.renderSuspend(renderRequest: RenderRequest): Result<RenderResponse> = suspendCancellableCoroutine { cont ->
        renderAsync(renderRequest, object: RenderCallback {
            override fun onSuccess(result: RenderResponse) {
                cont.resume(Result.success(result))
            }

            override fun onFailure(message: String) {
                cont.resume(Result.failure(RuntimeException("Pdf render failed: $message")))
            }

            override fun close() {}
        })
    }

suspend fun PdfRenderer.renderTileSuspend(tile: PdfTile, bufferInfo: BufferInfo): Result<Unit> = measureTimedValue {
    suspendCancellableCoroutine { cont ->
        renderTileAsync(tile, bufferInfo, object: TileRenderCallback {
            override fun onSuccess() {
                cont.resume(Result.success(Unit))
            }

            override fun onFailure(message: String) {
                cont.resume(Result.failure(RuntimeException("Pdf tile render failed: $message")))
            }

            override fun close() {}
        })
    }
}.also { /*d("Rendered $tile in ${it.duration}")*/ }.value

suspend fun PdfRenderer.getPageCountSuspend(): Result<Int> = suspendCancellableCoroutine { cont ->
    getPageCount(object: PdfOperationCallback {
        override fun onPageCount(count: Result<Int>) {
            cont.resume(count)
        }

        override fun onPageRatio(ratio: Result<Float>) {}

        override fun close() {
        }
    })
}


suspend fun PdfRenderer.getPageRatioSuspend(page: Int): Result<Float> = suspendCancellableCoroutine { cont ->
    getPageRatio(page, object: PdfOperationCallback {
        override fun onPageCount(count: Result<Int>) {}

        override fun onPageRatio(ratio: Result<Float>) {
            cont.resume(ratio)
        }

        override fun close() {
        }
    })
}

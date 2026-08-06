package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

expect class PdfRenderer: AutoCloseable {

    constructor(source: CustomPdfSourceAdapter)
    constructor(source: PdfSource)

    @JniCall
    suspend fun openDocument(): Result<Unit>

    @JniCall
    suspend fun render(page: Int, dimensions: PageDimensions, buffer: ImageBuffer)

    @JniCall
    suspend fun renderTile(tile: PdfTile, buffer: ImageBuffer)

    @JniCall
    suspend fun getPageCount(): Int
    @JniCall
    suspend fun getPageRatio(pageIndex: Int): Float

    override fun close()
}
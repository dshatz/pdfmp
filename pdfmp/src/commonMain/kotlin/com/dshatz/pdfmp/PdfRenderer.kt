package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.PdfSource

expect class PdfRenderer {
    fun render(renderRequest: RenderRequest): Result<RenderResponse>
    fun getPageCount(): Result<Int>
    fun getPageRatios(): Result<List<Float>>
    fun close()
}

expect object PdfRendererFactory {
    fun createFromSource(source: PdfSource): Result<PdfRenderer>
}

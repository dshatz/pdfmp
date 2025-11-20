package com.dshatz.pdfmp.compose

import com.dshatz.pdfmp.ImageTransform
import com.dshatz.pdfmp.PageSize
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.RenderResponse

actual class CurrentImage actual constructor(
    override val requestedTransform: ImageTransform,
    override val loadedTransform: ImageTransform,
    override val pageSize: PageSize,
    override val renderResponse: RenderResponse
) : ICurrentImage
package com.dshatz.pdfmp.compose

import com.dshatz.pdfmp.ImageTransform
import com.dshatz.pdfmp.PageSize
import com.dshatz.pdfmp.RenderResponse


interface ICurrentImage {
    val requestedTransform: ImageTransform
    val loadedTransform: ImageTransform
    val pageSize: PageSize
    val renderResponse: RenderResponse
}


expect class CurrentImage(
    requestedTransform: ImageTransform,
    loadedTransform: ImageTransform,
    pageSize: PageSize,
    renderResponse: RenderResponse
): ICurrentImage
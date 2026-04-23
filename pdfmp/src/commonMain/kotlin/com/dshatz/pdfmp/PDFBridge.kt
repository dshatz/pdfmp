package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.model.RenderRequest

/*
expect object PDFBridge {
    @JniCall
    fun renderAsync(
        renderer: PdfRendererPtr,
        request: RenderRequest,
        callback: RenderCallback
    )
    @JniCall
    fun getPageCount(renderer: PdfRendererPtr): Result<Int>
    @JniCall
    fun getPageRatio(renderer: PdfRendererPtr, pageIndex: Int): Result<Float>
    @JniCall
    fun close(renderer: PdfRendererPtr,)

    @JniCall
    fun initNative()
}*/

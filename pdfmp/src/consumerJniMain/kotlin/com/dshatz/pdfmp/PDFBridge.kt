package com.dshatz.pdfmp

object PDFBridge {
    external fun initNative()

    /**
     * Create the native PdfRenderer, call openFile on it and return the pointer.
     * @return packed Long ([PdfRendererPtr]).
     */
    external fun createNativeRenderer(packedSource: ByteArray): ByteArray

    /**
     * @return packed Int - page count in current document.
     */
    external fun getPageCount(renderer: PdfRendererPtr): ByteArray

    /**
     * @return packed List<Float> - page ratios for pages.
     */
    external fun getPageRatios(renderer: PdfRendererPtr): ByteArray
    external fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray
    external fun close(renderer: PdfRendererPtr)
}
package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.CustomPdfSourceAdapter

object PDFBridge {
    external fun initNative()

    /**
     * Create the native PdfRenderer, call openFile on it and return the pointer.
     * @param packedSource packed bytes of [com.dshatz.pdfmp.source.PdfSource].
     * @return packed Long ([PdfRendererPtr]).
     */
    external fun createNativeRenderer(packedSource: ByteArray): ByteArray

    /**
     * Create the native PdfRenderer, call openFile on it and return the pointer.
     * @param customSource instance of [com.dshatz.pdfmp.source.CustomPdfSourceAdapter] for retrieving document bytes.
     * @return packed Long ([PdfRendererPtr]).
     */
    external fun createNativeRendererCustom(customSource: CustomPdfSourceAdapter): ByteArray

    /**
     * @return packed Int - page count in current document.
     */
    external fun getPageCount(renderer: PdfRendererPtr): ByteArray

    /**
     * @return packed List<Float> - page ratios for pages.
     */
//    external fun getPageRatios(renderer: PdfRendererPtr): ByteArray
    external fun getAspectRatio(renderer: PdfRendererPtr, pageIndex: Int): ByteArray
    external fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray
    external fun close(renderer: PdfRendererPtr)
}
package com.dshatz.pdfmp

import java.nio.ByteBuffer

object PDFBridge {
    external fun initNative()
    external fun openFile(packedSource: ByteArray): PdfRendererPtr
    external fun getPageCount(renderer: PdfRendererPtr): Int
    external fun getPageRatios(renderer: PdfRendererPtr): ByteArray
    external fun getAspectRatio(renderer: PdfRendererPtr, pageIndex: Int): Float
    external fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray
    external fun getBufferAddress(buffer: ByteBuffer): Long
    external fun close(renderer: PdfRendererPtr)
}
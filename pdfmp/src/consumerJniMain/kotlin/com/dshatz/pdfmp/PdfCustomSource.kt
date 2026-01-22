package com.dshatz.pdfmp

import java.nio.ByteBuffer

interface PdfCustomSource {
    fun getDocumentLength(): Long
    fun readBlock(position: Long, buffer: ByteBuffer): Int
}
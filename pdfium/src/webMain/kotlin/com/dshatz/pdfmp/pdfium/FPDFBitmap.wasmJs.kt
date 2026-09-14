package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.Ptr
import kotlinx.serialization.Serializable

@Serializable
actual class PdfiumBitmap(
    val pdfiumPtr: Ptr,
    val bytes: Int,
    val memoryPtr: Ptr
)
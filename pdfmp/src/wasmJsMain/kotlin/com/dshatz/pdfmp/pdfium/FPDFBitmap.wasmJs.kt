package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.Ptr
import org.jetbrains.skia.Data

actual class PdfiumBitmap(
    val pdfiumPtr: Ptr,
    val bytes: Int
)
package com.dshatz.pdfmp.pdfium

import cnames.structs.fpdf_bitmap_t__
import kotlinx.cinterop.CPointer

actual class PdfiumBitmap(
    val bitmap: CPointer<fpdf_bitmap_t__>
)
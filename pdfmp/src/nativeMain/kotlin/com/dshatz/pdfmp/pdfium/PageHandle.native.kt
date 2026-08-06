package com.dshatz.pdfmp.pdfium

import cnames.structs.fpdf_page_t__
import kotlinx.cinterop.CPointer

actual class PageHandle(
    val ptr: CPointer<fpdf_page_t__>
)
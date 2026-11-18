package com.dshatz.pdfmp.pdfium

import cnames.structs.fpdf_page_t__
import com.dshatz.internal.pdfium.FPDF_FORMHANDLE
import kotlinx.cinterop.CPointer

actual class PageHandle(
    val ptr: CPointer<fpdf_page_t__>,
    /** The form-fill environment of the page's document. */
    val form: FPDF_FORMHANDLE? = null,
)

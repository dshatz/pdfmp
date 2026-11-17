package com.dshatz.pdfmp.pdfium

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.FPDF_FORMFILLINFO
import com.dshatz.internal.pdfium.FPDF_FORMHANDLE
import kotlinx.cinterop.CPointer

actual class DocHandle(
    val ptr: CPointer<fpdf_document_t__>,
    /** Form-fill environment, see [Pdfium.FFLDraw]. pdfium keeps using [formInfo] until it is closed. */
    val form: FPDF_FORMHANDLE? = null,
    val formInfo: FPDF_FORMFILLINFO? = null,
)

package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.FPDF_CloseDocument
import kotlinx.cinterop.CPointer

class DocInstance(
    val handle: CPointer<fpdf_document_t__>,
) : AutoCloseable {
    override fun close() {
        FPDF_CloseDocument(handle)
    }
}
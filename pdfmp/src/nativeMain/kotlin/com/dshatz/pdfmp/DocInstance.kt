package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.FPDF_CloseDocument
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.Pdfium
import kotlinx.cinterop.CPointer

class DocInstance(
    val handle: DocHandle,
) : AutoCloseable {
    override fun close() {
        Pdfium.CloseDocument(handle)
    }
}
package com.dshatz.pdfmp

import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.Pdfium

class DocInstance(
    val handle: DocHandle,
) : AutoCloseable {
    override fun close() {
        Pdfium.CloseDocument(handle)
    }
}
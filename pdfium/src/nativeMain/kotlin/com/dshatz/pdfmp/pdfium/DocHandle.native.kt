package com.dshatz.pdfmp.pdfium

import cnames.structs.fpdf_document_t__
import kotlinx.cinterop.CPointer

actual class DocHandle(
    val ptr: CPointer<fpdf_document_t__>
)
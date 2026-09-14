package com.dshatz.pdfmp.pdfium

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import kotlinx.cinterop.CPointer

actual class FileAccess(
    val fileAccessDescriptor: CustomSourceDescriptor
)

interface CustomSourceDescriptor {
    val pdfiumAccess: CPointer<FPDF_FILEACCESS>
}
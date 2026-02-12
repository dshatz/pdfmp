package com.dshatz.pdfmp.source

import kotlinx.cinterop.CPointer

actual data class CustomSourceDescriptorNative(
    actual val pdfiumAccess: CPointer<FPDF_FILEACCESS>
)
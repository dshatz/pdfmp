package com.dshatz.pdfmp.source

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import kotlinx.cinterop.CPointer

actual typealias CustomSourceDescriptor = CustomSourceDescriptorNative


expect class CustomSourceDescriptorNative {
    val pdfiumAccess: CPointer<FPDF_FILEACCESS>

    fun dispose()

    val sourceAdapter: CustomPdfSourceAdapter

    fun clearError()
}
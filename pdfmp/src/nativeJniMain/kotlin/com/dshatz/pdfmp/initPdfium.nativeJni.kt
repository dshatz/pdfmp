package com.dshatz.pdfmp

import com.dshatz.internal.pdfium.FPDF_InitLibrary
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual fun initPdfium() {
    FPDF_InitLibrary()
}
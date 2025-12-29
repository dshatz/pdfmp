package com.dshatz.pdfmp

import com.dshatz.internal.pdfium.FPDF_InitLibrary
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun initPdfium() {
    /*try {
        d("Initializing PDFium with Config...")

        memScoped {
            val config = alloc<FPDF_LIBRARY_CONFIG>()

            config.version = 2
            config.m_pUserFontPaths = null
            config.m_pIsolate = null
            config.m_v8EmbedderSlot = 0
            FPDF_InitLibraryWithConfig(config.ptr)
        }
        d("PDFium Initialized Successfully.")
    } catch (e: Exception) {
        d("Failed to init PDFium: ${e.message}")
    }*/
    FPDF_InitLibrary()
}
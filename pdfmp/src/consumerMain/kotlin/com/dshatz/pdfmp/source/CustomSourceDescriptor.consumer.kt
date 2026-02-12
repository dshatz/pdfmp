package com.dshatz.pdfmp.source

import com.dshatz.pdfmp.source.CustomPdfSourceAdapter

actual data class CustomSourceDescriptor(
    val jvmCustomSource: CustomPdfSourceAdapter
) {
    actual fun dispose() {
        jvmCustomSource.close()
    }

}
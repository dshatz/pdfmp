package com.dshatz.pdfmp.source

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.nativeHeap

class CustomSourceDescriptorNative(
    val pdfiumAccess: CPointer<FPDF_FILEACCESS>,
    internal val sourceRef: StableRef<CustomPdfSourceAdapter>,
) {
    fun close() {
        sourceRef.get().close()
        sourceRef.dispose()
        nativeHeap.free(pdfiumAccess.rawValue)
    }

    val sourceAdapter: CustomPdfSourceAdapter
        get() = sourceRef.get()
}
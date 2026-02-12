package com.dshatz.pdfmp.source

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.nativeHeap

actual class CustomSourceDescriptorNative(
    actual val pdfiumAccess: CPointer<FPDF_FILEACCESS>,
    internal val sourceRef: StableRef<CustomPdfSourceAdapter>,
) {
    actual fun dispose() {
        sourceRef.get().close()
        sourceRef.dispose()
        nativeHeap.free(pdfiumAccess.rawValue)
    }

    actual val lastError: String? get() = sourceRef.get().getLastError()
    actual fun clearError() {
        sourceRef.get().setError(null)
    }
}
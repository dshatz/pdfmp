package com.dshatz.pdfmp.source

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.nativeHeap

/*
actual data class CustomSourceDescriptor(
    val pdfiumAccess: CPointer<FPDF_FILEACCESS>,
    private val jniContext: StableRef<JniStaticMethodRef>
) {
    fun dispose() {
        jniContext.dispose()
        nativeHeap.free(pdfiumAccess.rawValue)
    }
}*/

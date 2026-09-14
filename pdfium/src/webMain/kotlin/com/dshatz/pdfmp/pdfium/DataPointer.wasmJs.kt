package com.dshatz.pdfmp.pdfium

import kotlinx.serialization.Serializable

@Serializable
actual data class DataPointer(val ptr: UInt) {
    actual companion object {
        actual fun fromLongPointer(ptr: Long): DataPointer {
            return DataPointer(ptr.toUInt())
        }
    }
}

package com.dshatz.pdfmp.pdfium

actual data class DataPointer(val ptr: UInt) {
    actual companion object {
        actual fun fromLongPointer(ptr: Long): DataPointer {
            return DataPointer(ptr.toUInt())
        }
    }
}

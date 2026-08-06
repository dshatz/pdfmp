package com.dshatz.pdfmp.pdfium

expect class DataPointer {
    companion object {
        fun fromLongPointer(ptr: Long): DataPointer
    }
}
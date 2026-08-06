package com.dshatz.pdfmp.imagebuffer

import kotlinx.cinterop.CPointer

interface WritableImageBuffer {
    fun <R> withWritableAddress(block: (address: CPointer<*>) -> R)
}
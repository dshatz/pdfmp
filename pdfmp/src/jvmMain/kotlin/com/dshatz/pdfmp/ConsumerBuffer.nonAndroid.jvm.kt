package com.dshatz.pdfmp

import org.jetbrains.skia.impl.NativePointer

actual fun NativePointer.toLong(): Long {
    return this
}
package com.dshatz.pdfmp

import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.impl.NativePointer

@OptIn(ExperimentalForeignApi::class)
actual fun NativePointer.toLong(): Long {
    return this.toLong()
}
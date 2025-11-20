package com.dshatz.pdfmp

actual object NativeLibLoader {
    actual fun load() {
        System.loadLibrary("pdfium")
        System.loadLibrary("pdfmp")
        PDFBridge.initNative()
    }

    init {
        load()
    }
}
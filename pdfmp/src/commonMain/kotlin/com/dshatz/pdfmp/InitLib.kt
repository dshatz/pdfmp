package com.dshatz.pdfmp

expect class InitLib: LibInitializer {
    override fun loadLibs()
}

interface LibInitializer {
    fun loadLibs()
    fun init() {
        loadLibs()
//        PDFBridge.initNative()
    }
}
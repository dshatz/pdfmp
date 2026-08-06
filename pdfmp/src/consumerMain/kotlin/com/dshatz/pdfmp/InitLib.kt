package com.dshatz.pdfmp

expect object InitLib: LibInitializer {
    override fun loadLibs()
}

interface LibInitializer {
    fun loadLibs()
    fun init() {
        loadLibs()
//        PDFBridge.initNative()
    }
}
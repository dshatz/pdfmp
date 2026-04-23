package com.dshatz.pdfmp

import com.dshatz.kni.load.BundledLibLoader

actual object InitLib: LibInitializer {
    var loaded: Boolean = false
    actual override fun loadLibs() {
        if (!loaded) {
            BundledLibLoader.loadBundledLibrary("pdfium")
            BundledLibLoader.loadBundledLibrary("pdfmp")
            loaded = true
        }
    }
}
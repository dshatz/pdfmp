package com.dshatz.pdfmp

import com.dshatz.kni.load.BundledLibLoader


actual object InitLib: LibInitializer {
    var loaded: Boolean = false
    actual override fun loadLibs() {
        if (!loaded) {
            d("Loading libs!")
            BundledLibLoader.loadBundledLibrary("pdfium")
            BundledLibLoader.loadBundledLibrary("pdfmp")
            d("Libs loaded!")
            loaded = true
        }
    }
}
package com.dshatz.pdfmp

import com.dshatz.kni.load.BundledLibLoader


actual object InitLib: LibInitializer {
    var loaded: Boolean = false
    actual override fun loadLibs() {
        if (!loaded) {
            d("Loading libs!")
            overrideLoadLibrary?.invoke("pdfium") ?: BundledLibLoader.loadBundledLibrary("pdfium")
            overrideLoadLibrary?.invoke("pdfmp") ?: BundledLibLoader.loadBundledLibrary("pdfmp")
            d("Libs loaded!")
            loaded = true
        }
    }


    var overrideLoadLibrary: ((name: String) -> Unit)? = null
}
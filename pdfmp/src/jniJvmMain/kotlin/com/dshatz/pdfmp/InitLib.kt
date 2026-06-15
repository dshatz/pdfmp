package com.dshatz.pdfmp

import com.dshatz.kni.load.BundledLibLoader

actual class InitLib: LibInitializer {
    actual override fun loadLibs() {
        BundledLibLoader.loadBundledLibrary("pdfium")
        BundledLibLoader.loadBundledLibrary("pdfmp")
    }
}
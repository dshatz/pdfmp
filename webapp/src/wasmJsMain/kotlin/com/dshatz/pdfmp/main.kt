package com.dshatz.pdfmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.dshatz.pdfmp.compose.platformModifier.OnPdfiumReady
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        ComposeViewport(document.body!!) {
            /*OnPdfiumReady {*/
                Sample()
            /*}*/
        }
    }
}
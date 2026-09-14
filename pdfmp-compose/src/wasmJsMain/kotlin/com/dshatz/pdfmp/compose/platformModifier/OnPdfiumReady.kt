package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
//import com.dshatz.pdfmp.pdfium.awaitPdfium

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
fun OnPdfiumReady(block: @Composable () -> Unit) {
    val ready by produceState(false) {
//        awaitPdfium()
        value = true
    }
    if (ready) {
        block()
    }
}
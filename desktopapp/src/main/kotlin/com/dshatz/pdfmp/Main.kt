package com.dshatz.pdfmp

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        val state = rememberWindowState(size = DpSize(1080.dp, 1080.dp))
        Window(onCloseRequest = ::exitApplication, state = state) {
//            DemoTabs(PdfSource.PdfPath(Path("/home/dshatz/Downloads/sample2.pdf")))
            Sample()
        }
    }
}
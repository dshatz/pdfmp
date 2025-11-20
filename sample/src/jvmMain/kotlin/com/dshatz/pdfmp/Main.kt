package com.dshatz.pdfmp

import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dshatz.pdfmp.compose.source.asyncPdfResource
import com.dshatz.pdfmp.compose.source.pdfResource
import com.dshatz.pdfmp.source.PdfSource
import io.github.vinceglb.filekit.FileKit
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import pdf_multiplatform.sample.generated.resources.Res

fun main() {
    NativeLibLoader.load()
    FileKit.init(appId = "PDFMP")
    application {
        val state = rememberWindowState(size = DpSize(1080.dp, 1080.dp))
        Window(onCloseRequest = ::exitApplication, state = state) {
//            DemoTabs(PdfSource.PdfPath(Path("/home/dshatz/Downloads/sample2.pdf")))
            val res by asyncPdfResource {
                Res.readBytes("files/sample.pdf")
            }
            res?.let { DemoTabs(it) }
        }
    }
}
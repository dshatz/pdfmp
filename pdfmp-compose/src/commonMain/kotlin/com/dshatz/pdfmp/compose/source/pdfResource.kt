package com.dshatz.pdfmp.compose.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.io.files.Path

@Composable
fun pdfResource(bytes: ByteArray): PdfSource.PdfBytes {
    return PdfSource.PdfBytes(bytes)
}

@Composable
fun pdfResource(path: Path): PdfSource.PdfPath {
    return PdfSource.PdfPath(path)
}

@Composable
fun asyncPdfResource(load: suspend () -> ByteArray): State<PdfSource.PdfBytes?> {
    return produceState<PdfSource.PdfBytes?>(null, load) {
        value = PdfSource.PdfBytes(load())
    }
}
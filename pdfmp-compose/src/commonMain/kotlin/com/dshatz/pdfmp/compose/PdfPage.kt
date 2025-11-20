package com.dshatz.pdfmp.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.state.rememberDocumentState
import com.dshatz.pdfmp.compose.state.rememberPageRenderState
import com.dshatz.pdfmp.source.PdfSource

@Composable
fun PdfPage(source: PdfSource, modifier: Modifier = Modifier) {
    val renderer = remember { PdfRenderer(source) }
    DisposableEffect(Unit) {
        onDispose {
            renderer.close()
        }
    }
    val pageState = rememberPageRenderState(rememberDocumentState())
    InternalPdfPage(renderer, pageState, 0, modifier = modifier)
}
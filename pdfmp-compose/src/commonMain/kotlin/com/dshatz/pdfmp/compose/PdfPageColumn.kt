package com.dshatz.pdfmp.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.state.rememberDocumentState
import com.dshatz.pdfmp.compose.state.rememberPageRenderState
import com.dshatz.pdfmp.source.PdfSource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPageColumn(source: PdfSource, modifier: Modifier = Modifier) {
    val renderer = remember(source) { PdfRenderer(source) }
    DisposableEffect(Unit) {
        onDispose {
            renderer.close()
        }
    }
    val pageCount by produceState<Int?>(null, renderer) {
        value = renderer.getPageCount()
        println("Page count $value")
    }
    val pageRatios by produceState<List<Float>>(emptyList(), source) {
        value = renderer.getPageRatios()
    }
    pageCount?.let { pageCount ->
        val listState = rememberLazyListState()
        val docState = rememberDocumentState()
        LazyColumn(modifier, state = listState) {
            items(pageRatios.size) { pageIdx ->
                val pageState = rememberPageRenderState(docState, pageIdx)

                Column(Modifier.requiredHeight(with (LocalDensity.current) { pageState.imageSize.value.height.toDp() } + 30.dp)) {
                    InternalPdfPage(renderer, pageState, pageIdx, Modifier.fillMaxWidth(), scrollable = false)
                }
            }
        }
    }
}
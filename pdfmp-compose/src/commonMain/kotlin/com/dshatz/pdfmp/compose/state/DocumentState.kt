package com.dshatz.pdfmp.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

@Composable
fun rememberDocumentState(): PdfDocumentState {
    return remember { PdfDocumentState() }
}

data class PdfDocumentState(
    val scale: MutableState<Float> = mutableFloatStateOf(1f),
    val viewport: MutableState<Size> = mutableStateOf(Size(1f, 1f)),
    val globalHorizontalOffset: MutableState<Float> = mutableFloatStateOf(0f)
) {

    fun setScale(value: Float) {
        scale.value = value
    }

    fun setViewport(size: Size) {
        viewport.value = size
    }
}

@Composable
fun rememberPageRenderState(docState: PdfDocumentState, key: Any = ""): PdfPageState {
    val offset = remember { mutableFloatStateOf(0f) }
    return remember(key, docState) {
        PdfPageState(
            docState = docState,
            verticalOffset = offset,
        )
    }
}
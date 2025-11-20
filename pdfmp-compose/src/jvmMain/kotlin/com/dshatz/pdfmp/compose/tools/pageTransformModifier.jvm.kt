package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import com.dshatz.pdfmp.compose.state.PdfPageState

actual fun Modifier.pageTransformModifier(pageState: PdfPageState, enablePan: Boolean): Modifier {
    return desktopZoom(pageState, enablePan)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.desktopZoom(pageState: PdfPageState, enablePan: Boolean): Modifier = composed {
    val density = LocalDensity.current
    onPointerEvent(PointerEventType.Scroll) { event ->
        // Desktop
        val change = event.changes.first()
        val delta = change.scrollDelta
        val cursorPosition = change.position

        if (event.keyboardModifiers.isCtrlPressed) {
            val zoomFactor = if (delta.y < 0) 1.1f else 0.9f
            val currentOffset = pageState.offsetState.value
            val currentScale = pageState.docState.scale.value

            val newScale = (currentScale * zoomFactor).coerceScale()

            val actualZoomFactor = if (currentScale == 0f) 1f else newScale / currentScale


            val mouseVector = cursorPosition - currentOffset

            val zoomAdjustment = mouseVector * (actualZoomFactor - 1f)
            pageState.docState.setScale(newScale)
            if (enablePan) pageState.dispatchScroll(-zoomAdjustment)
            change.consume()
        }
    }
}
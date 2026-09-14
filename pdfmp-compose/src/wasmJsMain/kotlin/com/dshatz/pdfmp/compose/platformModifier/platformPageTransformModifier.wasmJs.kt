package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.detectTransformGesturesHighPriority

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformPageTransformModifier(
    state: PdfState,
): Modifier = composed {
    this.pointerInput(Unit) {
        detectTransformGesturesHighPriority { centroid, _, zoom, _ ->
            state.zoomBy(zoom, centroid)
        }
    }.onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
        val eventChange = event.changes.firstOrNull() ?: return@onPointerEvent

        if (event.keyboardModifiers.isCtrlPressed) {
            eventChange.consume()

            val scrollDelta = eventChange.scrollDelta
            val zoomFactor = if (scrollDelta.y < 0) 1.1f else 0.9f

            state.zoomBy(
                zoomFactor = zoomFactor,
                centroid = eventChange.position
            )
        }
    }
}
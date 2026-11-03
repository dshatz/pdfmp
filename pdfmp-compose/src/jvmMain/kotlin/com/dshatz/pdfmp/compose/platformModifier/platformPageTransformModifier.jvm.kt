package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.dshatz.pdfmp.compose.state.PdfState

actual fun Modifier.platformPageTransformModifier(state: PdfState): Modifier = composed {
    desktopZoom(state)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.desktopZoom(
    state: PdfState
): Modifier = composed {
    val zoomStep = remember { WheelZoomStep(WheelZoomStep.smoothDeltaPerNotch(CurrentPlatform)) }
    onPointerEvent(PointerEventType.Companion.Scroll, PointerEventPass.Initial) { event ->
        val eventChange = event.changes.first()

        // A consumed event was handled by someone else, e.g. an app's own zoom handler further out.
        if (event.keyboardModifiers.isCtrlPressed && !eventChange.isConsumed) {
            eventChange.consume()

            val zoomFactor = zoomStep.zoomFactor(eventChange.scrollDelta.y, eventChange.uptimeMillis)
            if (zoomFactor != 1f) {
                state.zoomBy(
                    zoomFactor = zoomFactor,
                    centroid = eventChange.position
                )
            }
        }
    }
}
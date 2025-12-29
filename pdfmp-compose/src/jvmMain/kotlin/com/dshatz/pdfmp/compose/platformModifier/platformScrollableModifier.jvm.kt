package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.dshatz.pdfmp.compose.state.PdfState

actual fun Modifier.platformScrollableModifier(state: PdfState): Modifier = composed {
    biDirectionalScroll(state)
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.biDirectionalScroll(
    state: PdfState,
    speedMultiplier: Float = 30f
): Modifier {
    return this.onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
        val changes = event.changes

        val deltaX = changes.fold(0f) { acc, change -> acc + change.scrollDelta.x }
        val deltaY = -changes.fold(0f) { acc, change -> acc + change.scrollDelta.y }

        var eventConsumed = false

        if (deltaY != 0f) {
            val displacementY = deltaY * speedMultiplier
            state.onScroll(Offset(0f, displacementY))
            eventConsumed = true
        }

        if (deltaX != 0f) {
            val displacementX = deltaX * speedMultiplier
            state.onScroll(Offset(displacementX, 0f))
            eventConsumed = true
        }

        if (eventConsumed) {
            changes.forEach { it.consume() }
        }
    }
}
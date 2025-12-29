package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberScrollState
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
    if (CurrentPlatform == DesktopPlatform.MacOS) {
        biDirectionalScroll(state)
    } else {
        val vertical = rememberScrollableState {
            state.onScroll(Offset(0f, it)).y
        }
        val horizontal = rememberScrollableState {
            state.onScroll(Offset(it, 0f)).x
        }
        scrollable(vertical, Orientation.Vertical)
            .scrollable(horizontal, Orientation.Horizontal)
    }
}


enum class DesktopPlatform {
    Linux,
    Windows,
    MacOS,
    Unknown
}

private val CurrentPlatform: DesktopPlatform by lazy {
    val name = System.getProperty("os.name")
    when {
        name?.startsWith("Linux") == true -> DesktopPlatform.Linux
        name?.startsWith("Win") == true -> DesktopPlatform.Windows
        name == "Mac OS X" -> DesktopPlatform.MacOS
        else -> DesktopPlatform.Unknown
    }
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
package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollable2DState
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.dshatz.pdfmp.compose.state.PdfState

actual fun Modifier.platformScrollableModifier(state: PdfState): Modifier = composed {
    val scrollableState = rememberScrollable2DState { delta ->
        state.onScroll(delta)
        delta
    }
    scrollable2D(scrollableState, flingBehavior = ScrollableDefaults.flingBehavior())
}
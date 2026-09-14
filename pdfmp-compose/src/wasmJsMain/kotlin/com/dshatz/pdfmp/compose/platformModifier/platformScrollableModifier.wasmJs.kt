package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import com.dshatz.pdfmp.compose.state.PdfState

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.platformScrollableModifier(state: PdfState): Modifier = composed {
    val vertical = rememberScrollableState {
        state.onScroll(Offset(0f, it)).y
    }
    val horizontal = rememberScrollableState {
        state.onScroll(Offset(it, 0f)).x
    }
    scrollable(vertical, Orientation.Vertical)
        .scrollable(horizontal, Orientation.Horizontal)
}
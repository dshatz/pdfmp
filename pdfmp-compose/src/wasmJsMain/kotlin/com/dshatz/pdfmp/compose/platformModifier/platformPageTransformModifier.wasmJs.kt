package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.detectTransformGesturesHighPriority

actual fun Modifier.platformPageTransformModifier(
    state: PdfState,
): Modifier = composed {
    pointerInput(Unit) {
        detectTransformGesturesHighPriority { centroid, pan, zoom, _ ->
            state.zoomBy(zoom, centroid)
        }
    }
}
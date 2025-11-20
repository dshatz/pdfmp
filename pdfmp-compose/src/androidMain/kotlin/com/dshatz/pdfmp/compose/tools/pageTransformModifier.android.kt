package com.dshatz.pdfmp.compose.tools

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.dshatz.pdfmp.compose.state.PdfPageState
import kotlin.math.PI
import kotlin.math.abs

actual fun Modifier.pageTransformModifier(pageState: PdfPageState, enablePan: Boolean): Modifier = composed {
    val scale by pageState.docState.scale
    val density = LocalDensity.current

    pointerInput(Unit) {
        detectTransformGestures(consumePanGestures = enablePan) { centroid, pan, zoom, _ ->
            val oldScale = scale
            val newScale = (scale * zoom).coerceScale()

            val actualZoomFactor = if (oldScale == 0f) 1f else newScale / oldScale

            val offsetInContent = centroid - pageState.offsetState.value
            val delta = -offsetInContent * (actualZoomFactor - 1f) + pan

            pageState.dispatchScroll(delta)
            pageState.docState.setScale(newScale)
        }
    }
}

suspend fun PointerInputScope.detectTransformGestures(
    panZoomLock: Boolean = false,
    consumePanGestures: Boolean = true,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (
                        zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange

                    val isZoomOrRotate = effectiveRotation != 0f || zoomChange != 1f
                    val isPan = panChange != Offset.Zero

                    if (isZoomOrRotate || isPan) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }

                    event.changes.fastForEach {
                        val shouldConsumePosition =
                            isZoomOrRotate ||
                                    // Consume if it's a pan change AND we are configured to consume pans
                                    (isPan && consumePanGestures)

                        if (shouldConsumePosition && it.positionChanged()) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    }
}
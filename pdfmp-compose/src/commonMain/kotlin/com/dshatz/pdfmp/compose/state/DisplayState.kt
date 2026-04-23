package com.dshatz.pdfmp.compose.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Size

sealed class DisplayState {
    data object Initializing: DisplayState()
    data object Active: DisplayState()
    data class Error(val error: Throwable): DisplayState()
}


/**
 * Holds observable information about current document view such as scrolling and zooming.
 */
@Stable
class PdfLayoutInfo internal constructor(
    private val getOffsetY: () -> Float,
    private val getOffsetX: () -> Float,
    private val setOffsetY: (Float) -> Unit,
    private val getPageOffsetY: (pageIdx: Int) -> Float,
    private val getVisiblePages: () -> List<VisiblePageInfo>,
    private val getPageRange: () -> IntRange,
    private val getTotalHeight: () -> Float,
    private val getZoom: () -> Float,
    private val doZoom: (Float) -> Unit,
    private val getViewportSize: () -> Size
) {

    fun scrollTo(
        pageIdx: Int,
    ) {
        setOffsetY(getPageOffsetY(pageIdx))
    }

    internal fun pageOffsetY(idx: Int) = derivedStateOf {
        getPageOffsetY(idx)
    }

    /**
     * Scroll offset in pixels from the top of the document to the top of the current viewport.
     */
    var offsetY: Float
        get() = getOffsetY()
        set(value) = setOffsetY(value)

    internal val offsetX: Float
        get() = getOffsetX()

    private val animatableScroll = Animatable(0f)
    suspend fun animateScrollTo(
        pageIdx: Int
    ) {
        animatableScroll.snapTo(offsetY)
        animatableScroll.animateTo(
            targetValue = getPageOffsetY(pageIdx),
            animationSpec = spring(stiffness = Spring.StiffnessLow) // Soft spring
        ) {
            offsetY = value
        }
    }

    fun setZoom(newZoom: Float) {
        doZoom(newZoom)
    }

    private val animatableZoom = Animatable(0f)
    suspend fun animateSetZoom(newZoom: Float) {
        animatableZoom.snapTo(zoom.value)
        animatableZoom.animateTo(
            targetValue = newZoom,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        ) {
            setZoom(value)
        }
    }

    /**
     * Visibility info of pages that are currently in the viewport.
     */
    val visiblePages = derivedStateOf {
        getVisiblePages()
    }

    /**
     * The main page currently in view (by max height visibility).
     */
    val mostVisiblePage = derivedStateOf {
        visiblePages.value.maxByOrNull {
            it.visibilityPercentageH
        }
    }

    /**
     * Total number of pages displayed.
     */
    val totalPages = derivedStateOf {
        getPageRange().count()
    }

    /**
     * Page indices that are displayed in the document.
     *
     * These are all pages that user can scroll to, not only the currently visible ones.
     */
    val pageRange = derivedStateOf {
        getPageRange()
    }

    /**
     * Total height of the document in pixels. Can be larger than the viewport.
     */
    val documentHeight = derivedStateOf {
        getTotalHeight()
    }

    /**
     * Current zoom factor for the entire document. See [zoomPercents] to get the same as a percentage.
     */
    val zoom = derivedStateOf {
        getZoom()
    }

    val viewportSize = derivedStateOf {
        getViewportSize()
    }
}

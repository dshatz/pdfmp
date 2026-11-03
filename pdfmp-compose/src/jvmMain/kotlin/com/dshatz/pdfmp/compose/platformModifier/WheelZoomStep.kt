package com.dshatz.pdfmp.compose.platformModifier

import kotlin.math.pow
import kotlin.math.round

/**
 * Turns the scroll delta of a Ctrl+wheel event into a zoom factor.
 *
 * One wheel notch (scroll delta 1) zooms by [ZOOM_PER_NOTCH], so the factor is `1.1^-delta`.
 *
 * Smooth scrolling sources (trackpads, Magic Mouse, wheel smoothing tools such as Mac Mouse Fix) send a stream of
 * many fractional deltas instead. On macOS such a stream adds up to far more than one per notch: one notch of a
 * wheel through Mac Mouse Fix arrived as 151 events adding up to about 46. Counting each event as a notch zoomed
 * from 100 % to the 500 % limit in one notch. A delta in a smooth stream therefore counts as
 * `1 / smoothDeltaPerNotch` of a notch.
 *
 * Smooth streams also contain whole deltas (-1.0 and -2.0 among the fractions), so a stream is recognised by a
 * fractional delta within the last [SMOOTH_STREAM_GAP_MS], not by each event on its own.
 *
 * @param smoothDeltaPerNotch scroll delta that a smooth source sends for about one notch.
 */
internal class WheelZoomStep(
    private val smoothDeltaPerNotch: Float,
) {
    private var lastFractionalAt: Long? = null

    /**
     * @param scrollDelta vertical scroll delta of the event, negative when scrolling up (zoom in).
     * @param uptimeMillis time of the event.
     * @return factor to multiply the current zoom by, 1 when the event should not zoom.
     */
    fun zoomFactor(scrollDelta: Float, uptimeMillis: Long): Float {
        if (scrollDelta == 0f) return 1f
        if (scrollDelta != round(scrollDelta)) lastFractionalAt = uptimeMillis
        val smooth = lastFractionalAt?.let { uptimeMillis - it <= SMOOTH_STREAM_GAP_MS } == true
        val notches = (if (smooth) scrollDelta / smoothDeltaPerNotch else scrollDelta)
            .coerceIn(-MAX_NOTCHES_PER_EVENT, MAX_NOTCHES_PER_EVENT)
        return ZOOM_PER_NOTCH.pow(-notches)
    }

    companion object {
        const val ZOOM_PER_NOTCH = 1.1f

        /**
         * macOS accelerates fast wheel spins into large deltas; cap what one event can do.
         */
        const val MAX_NOTCHES_PER_EVENT = 3f

        const val SMOOTH_STREAM_GAP_MS = 300L

        /**
         * On macOS, from the Mac Mouse Fix measurement above. Elsewhere fractional deltas are already in notches
         * (AWT's precise wheel rotation), so no scaling.
         */
        fun smoothDeltaPerNotch(platform: DesktopPlatform): Float =
            if (platform == DesktopPlatform.MacOS) 40f else 1f
    }
}

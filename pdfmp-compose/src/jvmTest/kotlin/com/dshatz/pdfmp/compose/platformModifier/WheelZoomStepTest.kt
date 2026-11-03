package com.dshatz.pdfmp.compose.platformModifier

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.pow

val wheelZoomStepTests by testSuite {
    test("one notch zooms by 1.1, in and out") {
        val step = WheelZoomStep(smoothDeltaPerNotch = 40f)
        step.zoomFactor(-1f, 0) shouldBe (1.1f plusOrMinus 1e-4f)
        step.zoomFactor(1f, 1000) shouldBe (1 / 1.1f plusOrMinus 1e-4f)
    }

    test("factor follows the delta") {
        val step = WheelZoomStep(smoothDeltaPerNotch = 40f)
        step.zoomFactor(-2f, 0) shouldBe (1.1f.pow(2) plusOrMinus 1e-4f)
        step.zoomFactor(0f, 1000) shouldBe 1f
    }

    test("accelerated delta is capped") {
        val step = WheelZoomStep(smoothDeltaPerNotch = 40f)
        step.zoomFactor(-20f, 0) shouldBe (1.1f.pow(3) plusOrMinus 1e-4f)
    }

    test("smooth stream on macOS zooms about one notch per notch") {
        val step = WheelZoomStep(WheelZoomStep.smoothDeltaPerNotch(DesktopPlatform.MacOS))
        // Like one notch through Mac Mouse Fix: 151 events adding up to about 46, including whole deltas.
        val deltas = List(150) { if (it % 50 == 10) -1f else -0.28f } + -2f
        var zoom = 1f
        deltas.forEachIndexed { i, delta -> zoom *= step.zoomFactor(delta, i.toLong()) }
        deltas.sum() shouldBe (-46f plusOrMinus 1f)
        // Counting every event as a notch would have been 1.1^151.
        zoom shouldBe (1.1f.pow(-deltas.sum() / 40f) plusOrMinus 0.01f)
    }

    test("whole delta long after a smooth stream is a notch again") {
        val step = WheelZoomStep(smoothDeltaPerNotch = 40f)
        step.zoomFactor(-0.5f, 0)
        step.zoomFactor(-1f, 100) shouldBe (1.1f.pow(1f / 40f) plusOrMinus 1e-4f)
        step.zoomFactor(-1f, 1000) shouldBe (1.1f plusOrMinus 1e-4f)
    }

    test("fractions are already notches outside macOS") {
        val step = WheelZoomStep(WheelZoomStep.smoothDeltaPerNotch(DesktopPlatform.Windows))
        step.zoomFactor(-0.5f, 0) shouldBe (1.1f.pow(0.5f) plusOrMinus 1e-4f)
    }
}

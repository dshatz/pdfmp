package com.dshatz.pdfmp.compose

import androidx.compose.ui.geometry.Offset
import com.dshatz.pdfmp.compose.state.PdfState
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

val pdfStateScrollZoomTests by testSuite {
    test("scrollBy scrolls towards the end for positive y") {
        // 10 pages of 1000x1000 in a 1000x1500 viewport.
        val state = getMockedState()

        state.scrollBy(Offset(0f, 400f)) shouldBe Offset(0f, 400f)

        state.visiblePages.value.first().topCutoff shouldBe 400f
    }

    test("scrollBy returns only what was scrolled at the edges") {
        val state = getMockedState()

        state.scrollBy(Offset(0f, -100f)) shouldBe Offset.Zero
        // Document is 10000 high, viewport 1500.
        state.scrollBy(Offset(0f, 20000f)) shouldBe Offset(0f, 8500f)
        // Page is as wide as the viewport at zoom 1.
        state.scrollBy(Offset(100f, 0f)) shouldBe Offset.Zero
    }

    test("scrollBy scrolls horizontally when zoomed in") {
        val state = getMockedState()
        state.zoomBy(2f, Offset.Zero)

        state.scrollBy(Offset(300f, 0f)) shouldBe Offset(300f, 0f)

        state.visiblePages.value.first().leftCutoff shouldBe 300
    }

    test("zoomBy keeps the point under the centroid in place") {
        val state = getMockedState()

        state.zoomBy(2f, Offset(500f, 750f))

        // The document point at (500, 750) is now at (1000, 1500) and still under the centroid.
        val first = state.visiblePages.value.first()
        first.scaledWidth shouldBe 2000f
        first.leftCutoff shouldBe 500
        first.topCutoff shouldBe 750f
    }

    test("scrollBy and zoomBy do nothing before the document is shown") {
        val state = PdfState(scope = CoroutineScope(Dispatchers.Default))

        shouldNotThrowAny {
            state.scrollBy(Offset(0f, 100f)) shouldBe Offset.Zero
            state.zoomBy(2f, Offset.Zero)
        }
    }
}

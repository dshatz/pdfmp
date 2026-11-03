package com.dshatz.pdfmp.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.compose.state.VisiblePageInfo
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

val pdfVisiblePagesTests by testSuite {
    test("simple") {
        val state = getMockedState()
//        state.onScroll(Offset(0f, 0f))
        val visible = state.visiblePages.value

        val size = 1000f
        visible shouldContain VisiblePageInfo(
            0,
            scaledWidth = size,
            scaledHeight = size
        )

        visible shouldContain VisiblePageInfo(
            1,
            bottomCutoff = 500f,
            scaledWidth = size,
            scaledHeight = size
        )

        state.pages.size shouldBe 10
    }

    test("with gap") {
        val state = getMockedState(gap = 100)
//        state.onScroll(Offset(0f, 0f))
        val visible = state.visiblePages.value

        visible shouldContain VisiblePageInfo(
            0,
            scaledWidth = 1000f,
            scaledHeight = 1000f,
        )
        // Offset from top of the document for page1. 1000px (fist page) + 100px (gap) = 1100px
        // Viewport height = 1500px
        // Bottom cutoff page1 = page height - available space = 1000px - (1500-1100) = 1000 - 400 = 600px
        visible shouldContain VisiblePageInfo(
            1,
            bottomCutoff = 600f,
            scaledWidth = 1000f,
            scaledHeight = 1000f,
            topGap = 100
        )

        state.pages.size shouldBe 10
    }

    test("with gap scrolled") {
        val state = getMockedState(gap = 100, viewportSize = Size(1000f, 1000f))
        // scroll by 100 - viewport bottom is at end of gap
        state.onScroll(Offset(0f, -100f))

        state.visiblePages.value.single() shouldBe VisiblePageInfo(
            0,
            topCutoff = 100f,
            scaledWidth = 1000f,
            scaledHeight = 1000f,
        )

        // scroll by 1 - page 2 appears.
        state.onScroll(Offset(0f, -1f))

        state.visiblePages.value.size shouldBe 2

        state.visiblePages.value.first() shouldBe VisiblePageInfo(
            0,
            topCutoff = 101f,
            scaledWidth = 1000f,
            scaledHeight = 1000f,
        )

        state.visiblePages.value[1] shouldBe VisiblePageInfo(
            1,
            bottomCutoff = 999f, // just top 1px visible
            scaledWidth = 1000f,
            scaledHeight = 1000f,
            topGap = 100
        )
    }

    test("scaled") {
        val state = getMockedState(gap = 100, viewportSize = Size(1000f, 2500f))
        state.zoomBy(2f, Offset.Zero)
        // Zoom with mouse at 0,0

        // scaled 2x so width is 2000 and height is 2000 but viewport is still 1000x2500.
        // First page does not fit.
        state.visiblePages.value shouldBe listOf(
            VisiblePageInfo(
                0,
                rightCutoff = 1000,
                scaledWidth = 2000f,
                scaledHeight = 2000f
            ),
            VisiblePageInfo(
                1,
                bottomCutoff = 1700f,
                rightCutoff = 1000,
                scaledWidth = 2000f,
                scaledHeight = 2000f,
                topGap = 200
            )
        )
    }

    test("scrolled") {
        val state = getMockedState()
        // First item is taking 1000-400=600 of the viewport (900 remaining) Second is taking remaining 900 with 100 cut off.
        state.onScroll(Offset(0f, -400f))
        val visible = state.visiblePages.value
        visible shouldBe listOf(
            VisiblePageInfo(
                0,
                topCutoff = 400f,
                scaledWidth = 1000f,
                scaledHeight = 1000f
            ),
            VisiblePageInfo(
                1,
                bottomCutoff = 100f,
                scaledWidth = 1000f,
                scaledHeight = 1000f
            ),
        )
    }

    test("scrolled to end") {
        val state = getMockedState()
        state.onScroll(Offset(0f, -Float.MAX_VALUE))
        val visible = state.visiblePages.value
        visible shouldBe listOf(
            VisiblePageInfo(
                8,
                topCutoff = 500f,
                scaledWidth = 1000f,
                scaledHeight = 1000f
            ),
            VisiblePageInfo(
                9,
                scaledWidth = 1000f,
                scaledHeight = 1000f
            ),
        )
    }

    test("zoom is clamped to 1..5") {
        val state = getMockedState()
        state.zoomBy(100f, Offset.Zero)
        state.scale.value shouldBe 5f
        state.zoomBy(0.001f, Offset.Zero)
        state.scale.value shouldBe 1f
    }

    test("zoom keeps the point under the centroid fixed") {
        val state = getMockedState()
        state.onScroll(Offset(0f, -400f))
        state.zoomBy(2f, Offset(0f, 100f)) // absolute y 500 becomes 1000, minus centroid 100
        state.visiblePages.value.first().topCutoff shouldBe 900f
    }

    test("scroll is clamped to the document") {
        val state = getMockedState()
        state.onScroll(Offset(-500f, 500f)) // no horizontal room at 1x, nothing above the top
        state.visiblePages.value.first() shouldBe VisiblePageInfo(0, scaledWidth = 1000f, scaledHeight = 1000f)
    }

    test("pagesToRender adds neighbours of the visible pages") {
        val state = getMockedState(viewportSize = Size(1000f, 1000f))
        state.onScroll(Offset(0f, -2500f)) // pages 2 and 3 are visible
        state.pagesToRender.value shouldBe listOf(2, 3, 1, 4)
    }

    test("spacing scales with zoom and truncates") {
        getMockedState(gap = 10).scaledPageSpacing(1.55f) shouldBe 15
    }
}

package com.dshatz.pdfmp.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.state.VisiblePageInfo
import com.dshatz.pdfmp.source.PdfSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path

class PdfVisiblePagesTest: DescribeSpec({

    describe("visible pages") {
        it("simple") {
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

        it("with gap") {
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

        it("with gap scrolled") {
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

        it("scaled") {
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

        it("scrolled") {
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

        it("scrolled to end") {
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
    }
})

private fun getMockedState(
    pageRatio: Float = 1f,
    pageCount: Int = 10,
    gap: Int = 0,
    viewportSize: Size = Size(1000f, 1500f),
): PdfState {
    val scope = CoroutineScope(Dispatchers.Default)
    val renderer = mockk<PdfRenderer>()
    val state = PdfState(PdfSource.PdfPath(Path("")), pageSpacing = gap, scope = scope)
    every { renderer.getPageRatio(any()) } returns Result.success(pageRatio )
    every { renderer.getPageCount() } returns Result.success(pageCount)
    state.initPages(renderer)
    state.bind(LazyListState(0, 0), ScrollState(0))

    state.isInitialized.value = true

    state.setViewport(viewportSize)
    return state
}

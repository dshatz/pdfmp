package com.dshatz.pdfmp.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.PdfTile
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

val VisibleTilesTest by testSuite {
    test("default") {
        val state = getMockedState()
        val tiles = state.visibleTiles.value
        val scaledWidth = state.scaledPageWidth(state.viewport, state.scale)
        val th = state.scaledPageHeight(0, scaledWidth).toInt()
        val tw = scaledWidth.toInt()


        // Page 0 takes 1000 height
        (0 until th step PdfTile.HEIGHT).forEach { y ->
            (0 until tw step PdfTile.WIDTH).forEach { x ->
                tiles shouldContain PdfTile(0, x, y, tw, th).also { println(it) }
            }
        }

        // 500 remaining height for page 1
        (0 until 500 step PdfTile.HEIGHT).forEach { y ->
            (0 until tw step PdfTile.WIDTH).forEach { x ->
                tiles shouldContain PdfTile(1, x, y, tw, th).also { println(it) }
            }
        }
        tiles.count { it.page == 0 } shouldBe 4*4
        tiles.count { it.page == 1 } shouldBe 2*4
    }

    test("scrolled") {
        val state = getMockedState(viewportSize = Size(1000f, 1000f))
        // Scroll down so page 0 and page 1 are both half visible
        state.onScroll(Offset(0f, -500f))
        val tiles = state.visibleTiles.value
        val scaledWidth = state.scaledPageWidth(state.viewport, state.scale)
        val th = state.scaledPageHeight(0, scaledWidth).toInt()
        val tw = scaledWidth.toInt()

        (256 until 1024 step PdfTile.HEIGHT).forEach { y ->
            (0 until tw step PdfTile.WIDTH).forEach { x ->
                tiles shouldContain PdfTile(0, x, y, tw, th).also { println(it) }
            }
        }
        // We scrolled 500 so only the first row of 256 is hidden. The second row is necessary to display the remaining 12px.
        // Since normally 1000px page has 4 tiles, now it has 3 visible (12px + 256px + 232px)
        tiles.count { it.page == 0 } shouldBe 4*3

        (0 until 512 step PdfTile.HEIGHT).forEach { y ->
            (0 until tw step PdfTile.WIDTH).forEach { x ->
                tiles shouldContain PdfTile(1, x, y, tw, th).also { println(it) }
            }
        }

        tiles.count { it.page == 1 } shouldBe 4*2
    }

    test("zoomed") {
        val state = getMockedState(viewportSize = Size(1000f, 1000f))
        state.zoomTowardsCenter(2f)
        val tiles = state.visibleTiles.value
        // Zoom 2x towards center - means that viewport is now centered on middle of page 0.
        // Half of it is not visible in each axis (25% on each side)
        println(tiles)
    }
}
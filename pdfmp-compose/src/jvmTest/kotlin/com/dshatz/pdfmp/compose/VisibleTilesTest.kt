package com.dshatz.pdfmp.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.PageDimensions
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.TileKey
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

// Pages are 1000x1000 and the viewport 1000 wide: 4 columns of tiles (the 1 column side margin is off the page).
// Visible tiles also include 2 rows above and below the viewport, after the tiles on screen.

private fun tiles(page: Int, rows: IntRange, size: Int = 1000) = rows.flatMap { row ->
    (0..3).map { col ->
        PdfTile(TileKey(page, col * PdfTile.WIDTH, row * PdfTile.HEIGHT), PageDimensions(size, size))
    }
}

val VisibleTilesTest by testSuite {
    test("default") {
        // Viewport 0..1500: all of page 0, the top half of page 1. Prefetch reaches 2012.
        val tiles = getMockedState().visibleTiles.value

        val onScreen = tiles(0, 0..3) + tiles(1, 0..1)
        val prefetched = tiles(1, 2..3) + tiles(2, 0..0)
        tiles.take(onScreen.size).toSet() shouldBe onScreen.toSet()
        tiles.drop(onScreen.size).toSet() shouldBe prefetched.toSet()
    }

    test("scrolled") {
        // Viewport 500..1500: page 0 from its second row (256 is hidden), page 1 down to 500.
        val state = getMockedState(viewportSize = Size(1000f, 1000f))
        state.onScroll(Offset(0f, -500f))
        val tiles = state.visibleTiles.value

        val onScreen = tiles(0, 1..3) + tiles(1, 0..1)
        val prefetched = tiles(0, 0..0) + tiles(1, 2..3) + tiles(2, 0..0)
        tiles.take(onScreen.size).toSet() shouldBe onScreen.toSet()
        tiles.drop(onScreen.size).toSet() shouldBe prefetched.toSet()
    }

    test("zoomed") {
        val state = getMockedState(viewportSize = Size(1000f, 1000f))
        state.zoomTowardsCenter(2f)
        val tiles = state.visibleTiles.value
        tiles.shouldNotBeEmpty()
        // Pages are 2000 wide now, so all tiles are of the zoomed page size.
        tiles.map { it.scaledPage.width }.toSet() shouldBe setOf(2000)
    }
}

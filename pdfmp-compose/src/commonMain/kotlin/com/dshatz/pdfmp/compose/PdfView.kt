package com.dshatz.pdfmp.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.compose.platformModifier.platformScrollableModifier
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.drawPixmap
import com.dshatz.pdfmp.compose.tools.pageTransformModifier
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Display a PDF document from the given [state].
 *
 * [contentPadding] adds scrollable space around the pages like in `LazyColumn`, e.g. the navigation bar
 * height as bottom padding, so the end of the document can scroll above it. Only the top and bottom values
 * are used; the pages always span the full viewport width.
 *
 * Please enforce the size using either `Modifier.fillMaxSize` or `Modifier.size`.
 */
@Composable
fun PdfView(
    state: PdfState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues.Zero,
) = key(state) {
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()

    val density = LocalDensity.current
    val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    SideEffect {
        state.topPadding.floatValue = topPaddingPx
        state.bottomPadding.floatValue = bottomPaddingPx
    }

    if (state.isInitialized.value) {
        LaunchedEffect(Unit) {
            state.bind(listState, horizontalScroll)
        }

        Box(
            modifier = modifier.pageTransformModifier(state).onGloballyPositioned {
                // Report real viewport size.
                state.setViewport(it.size.toSize())
            }
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = false,
                modifier = Modifier.matchParentSize().platformScrollableModifier(state),
            ) {
                fullDocumentBoxes(state)
            }
            FullPages(
                state,
                modifier = Modifier.matchParentSize()
            )
            TiledViewport(state, Modifier.matchParentSize())
        }
    }
}


private fun LazyListScope.fullDocumentBoxes(state: PdfState) {
    state.pages.forEach { (pageIdx, _) ->
        item(pageIdx) {
            val density = LocalDensity.current
            val size by state.rememberScaledPageSize(pageIdx)
            val bottomPadding = if (pageIdx != state.pageRange.last) state.scaledPageSpacing() else 0
            Column(Modifier
                .requiredSize(size)
                .padding(bottom = with(density) { bottomPadding.toDp() })) {
            }
        }
    }
}

@Composable
private fun TiledViewport(
    state: PdfState,
    modifier: Modifier = Modifier
) {
    val tiles by state.visibleTiles

    // Every rendered tile, of the current scale or not: after a zoom, the old tiles stay until the new ones arrive.
    val renderedTiles = remember { mutableStateMapOf<PdfTile, RenderedTile>() }

    // Revealed a few per frame: each one is uploaded to the GPU on its first draw.
    val pendingTiles = remember { mutableStateMapOf<PdfTile, ImageBuffer>() }
    val revealSignal = remember { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        var order = 0L
        for (signal in revealSignal) {
            while (pendingTiles.isNotEmpty()) {
                withFrameNanos { }
                pendingTiles.keys.take(MAX_NEW_TILES_PER_FRAME).forEach { tile ->
                    val buffer = pendingTiles.remove(tile) ?: return@forEach
                    // Not if tilesToFree freed the tile meanwhile: its buffer may already belong to another tile.
                    if (state.ownsTileBuffer(tile, buffer)) renderedTiles[tile] = RenderedTile(buffer, order++)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { tilesToFree(renderedTiles.keys, state.visibleTiles.value) }
            .collect { tiles ->
                tiles.forEach { tile ->
                    renderedTiles.remove(tile)
                    state.freeTile(tile)
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            (renderedTiles.keys + pendingTiles.keys).forEach { state.freeTile(it) }
            renderedTiles.clear()
            pendingTiles.clear()
        }
    }

    val scope = rememberCoroutineScope() { Dispatchers.Default }
    for (tile in tiles) {
        key(tile) {
            DisposableEffect(tile) {
                val job = scope.launch {
                    // Short debounce: skip tiles that only pass by while scrolling. Rendering a tile takes ~1ms.
                    delay(100.milliseconds)
                    try {
                        val buffer = state.renderTile(tile)
                        if (scope.isActive) {
                            // Revealed even if this tile was disposed meanwhile: tilesToFree then frees it.
                            pendingTiles[tile] = buffer
                            revealSignal.trySend(Unit)
                        } else {
                            // TiledViewport left composition while rendering: nothing would reveal or free it.
                            state.freeTile(tile)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                onDispose {
                    job.cancel()
                }
            }
        }
    }
    val display by state.layoutInfo()
    display?.let { currentDisplay ->
        // Read offsets in the draw phase, so scrolling happens on the GPU.
        val currentScaledPages = remember(tiles) {
            tiles.associate { it.key.page to it.scaledPage }
        }
        Spacer(
            modifier = modifier
                .clipToBounds()
                .drawBehind {
                    translate(-currentDisplay.offsetX, -currentDisplay.offsetY) {
                        // Oldest first: the tiles of the current scale, rendered last, cover those of previous ones.
                        for ((tile, rendered) in renderedTiles.entries.sortedBy { it.value.order }) {
                            val scaledPage = currentScaledPages[tile.key.page] ?: continue
                            // 1.0 for tiles of the current scale; stretches those of a previous scale to it.
                            val factor = scaledPage.width.toFloat() / tile.scaledPage.width
                            translate(top = currentDisplay.currentPageOffsetY(tile.key.page)) {
                                scale(factor, pivot = Offset.Zero) {
                                    drawContext.drawPixmap(
                                        rendered.buffer,
                                        dstOffset = Offset(tile.key.x.toFloat(), tile.key.y.toFloat()),
                                    )
                                }
                            }
                        }
                    }
                }
        )
    }
}


@Composable
private fun FullPages(
    state: PdfState,
    modifier: Modifier = Modifier
) {
    val renderedMap = remember { mutableStateMapOf<Int, ImageBuffer>() }
    val visiblePages by state.visiblePages
    val scope = rememberCoroutineScope()
    val pagesToRender by state.pagesToRender
    for (pageIdx in pagesToRender) {
        key(pageIdx) {
            DisposableEffect(pageIdx) {
                val job = scope.launch {
                    try {
                        val prefetch = state.visiblePages.value.none { it.pageIdx == pageIdx }
                        val buffer = state.renderFullPage(pageIdx, prefetch)
                        currentCoroutineContext().ensureActive()
                        renderedMap[pageIdx] = buffer
                    } catch (e: CancellationException) {
                        state.freePage(pageIdx)
                        throw e
                    } catch (e: Exception) {
                        state.freePage(pageIdx)
                        e.printStackTrace()
                    }
                }
                onDispose {
                    job.cancel()
                    renderedMap.remove(pageIdx)
                    state.freePage(pageIdx)
                }
            }
        }
    }
    val visiblePageIndices by remember {
        derivedStateOf {
            visiblePages.map { it.pageIdx }
        }
    }
    val display by state.layoutInfo()
    display?.let { currentDisplay ->
        Spacer(
            modifier = modifier
                .clipToBounds()
                .drawBehind {
                    translate(-currentDisplay.offsetX, -currentDisplay.offsetY) {
                        for (page in visiblePageIndices) {
                            val buffer = renderedMap[page]
                            translate(top = currentDisplay.currentPageOffsetY(page)) {
                                scale(state.scale.value, pivot = Offset.Zero) {
                                    val pageSize = state.fullPageSize(page)
                                    if (buffer == null) {
                                        // Not rendered yet: show a blank page instead of the background.
                                        drawRect(Color.White, size = pageSize.toSize())
                                    } else {
                                        // The buffer is rendered at FULL_PAGE_RESOLUTION; stretch it to the page size.
                                        drawContext.drawPixmap(
                                            buffer = buffer,
                                            dstSize = pageSize,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        )
    }
}

/** A rendered tile, see TiledViewport. [order] grows with every revealed tile: higher is newer. */
private class RenderedTile(val buffer: ImageBuffer, val order: Long)

/**
 * Tiles outside the current [area] (viewport and prefetch margin) to free: right away if they don't overlap it,
 * otherwise once the whole area is rendered, so no gap shows the full page in between.
 */
private fun tilesToFree(rendered: Set<PdfTile>, area: List<PdfTile>): List<PdfTile> {
    val areaTiles = area.toSet()
    val outside = rendered.filter { it !in areaTiles }
    if (areaTiles.all { it in rendered }) return outside
    return outside.filterNot { it.overlapsAny(area) }
}

/** Whether this tile of a previous scale, stretched to the current scale, overlaps any of the [current] tiles. */
private fun PdfTile.overlapsAny(current: List<PdfTile>): Boolean {
    val samePage = current.filter { it.key.page == key.page }
    val scaledPage = samePage.firstOrNull()?.scaledPage ?: return false
    val factor = scaledPage.width.toFloat() / this.scaledPage.width
    val left = key.x * factor
    val top = key.y * factor
    val right = left + PdfTile.WIDTH * factor
    val bottom = top + PdfTile.HEIGHT * factor
    return samePage.any {
        it.key.x < right && it.key.x + PdfTile.WIDTH > left && it.key.y < bottom && it.key.y + PdfTile.HEIGHT > top
    }
}

/** Upper bound of newly rendered tiles revealed per frame (256 KB of texture upload each). */
private const val MAX_NEW_TILES_PER_FRAME = 8

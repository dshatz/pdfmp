package com.dshatz.pdfmp.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.compose.platformModifier.platformScrollableModifier
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.bufferColorFilter
import com.dshatz.pdfmp.compose.tools.drawPixmap
import com.dshatz.pdfmp.compose.tools.pageTransformModifier
import com.dshatz.pdfmp.d
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Display a PDF document from the given [state].
 *
 * Please enforce the size using either `Modifier.fillMaxSize` or `Modifier.size`.
 */
@Composable
fun PdfView(
    state: PdfState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()

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
                .padding(bottom = with(density) { bottomPadding.toDp() }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
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

    val renderedMap = remember { mutableStateMapOf<PdfTile, ImageBuffer>() }

    val scope = rememberCoroutineScope() { Dispatchers.Default }
    for (tile in tiles) {
        key(tile) {
            DisposableEffect(tile) {
                val job = scope.launch {
                    try {
                        // Short debounce: skip tiles that only pass by while scrolling. Rendering a tile takes ~1ms.
                        delay(50.milliseconds)
                        val buffer = state.renderTile(tile)
                        renderedMap[tile] = buffer
                    } catch (e: CancellationException) {
                        state.freeTile(tile)
                        throw e
                    } catch (e: Exception) {
                        state.freeTile(tile)
                        e.printStackTrace()
                    }
                }
                onDispose {
                    job.cancel()
                    renderedMap.remove(tile)
                    state.freeTile(tile)
                }
            }
        }
    }
    val display by state.layoutInfo()
    display?.let { currentDisplay ->
        // A single node draws every tile. Offsets are read in the draw phase, so scrolling only redraws:
        // no recomposition, no layer per tile and no per-tile nodes to hit-test.
        Spacer(
            modifier = modifier
                .clipToBounds()
                .drawBehind {
                    translate(-currentDisplay.offsetX, -currentDisplay.offsetY) {
                        for (tile in tiles) {
                            val buffer = renderedMap[tile] ?: continue
                            drawContext.drawPixmap(
                                buffer,
                                dstOffset = Offset(
                                    tile.key.x.toFloat(),
                                    tile.key.y + currentDisplay.currentPageOffsetY(tile.key.page)
                                ),
                            )
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
                        w("Replacing buffer with new one! Old buffer: ${renderedMap[pageIdx]}")
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
        // Like the tiles: one node, offsets and scale read in the draw phase.
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

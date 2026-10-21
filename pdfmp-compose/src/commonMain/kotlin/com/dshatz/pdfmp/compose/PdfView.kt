package com.dshatz.pdfmp.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
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
private fun BoxScope.PdfTileItem(
    tile: PdfTile,
    buffer: ImageBuffer?,
    pageOffsetY: Float
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                translationX = tile.key.x.toFloat()
                translationY = tile.key.y.toFloat() + pageOffsetY
            }
            .drawBehind {
                if (buffer != null) {
                    drawContext.drawPixmap(
                        buffer,
                        colorFilter = bufferColorFilter
                    )
                }
            }
    )
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
                        delay(200)
                        val buffer = state.renderTile(tile)
                        renderedMap[tile] = buffer
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                onDispose {
                    job.cancel()
                    renderedMap.remove(tile)?.let { buffer ->
                        state.freeTile(tile)
                    }
                }
            }
        }
    }
    val display by state.layoutInfo()
    display?.let { currentDisplay ->
        Box(
            modifier = modifier
                .clipToBounds()
                .graphicsLayer {
                    translationX = -currentDisplay.offsetX
                    translationY = -currentDisplay.offsetY
                }
        ) {
            tiles.forEach { tile ->
                val buffer = renderedMap[tile]
                val pageOffsetYState = currentDisplay.pageOffsetY(tile.key.page)

                key(tile) {
                    PdfTileItem(
                        tile = tile,
                        buffer = buffer,
                        pageOffsetY = pageOffsetYState.value
                    )
                }
            }

            Text(
                "Tiles: ${renderedMap.size}",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        translationX = currentDisplay.offsetX
                        translationY = currentDisplay.offsetY
                    },
                style = MaterialTheme.typography.headlineMedium
            )
        }
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
    for (page in visiblePages) {
        key(page.pageIdx) {
            DisposableEffect(page.pageIdx) {
                val job = scope.launch {
                    val buffer = state.renderFullPage(page.pageIdx)
                    currentCoroutineContext().ensureActive()
                    w("Replacing buffer with new one! Old buffer: ${renderedMap[page.pageIdx]}")
                    renderedMap[page.pageIdx] = buffer
                }
                onDispose {
                    job.cancel()

                    renderedMap.remove(page.pageIdx)?.let { buffer ->
                        state.freePage(page.pageIdx)
                    }
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
        Box(
            modifier = modifier
                .clipToBounds()
                .graphicsLayer {
                    translationX = -currentDisplay.offsetX
                    translationY = -currentDisplay.offsetY
                }
        ) {
            visiblePageIndices.forEach { page ->
                key(page) {
                    val buffer = renderedMap[page]
                    val pageOffset = currentDisplay.pageOffsetY(page).value
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationY = pageOffset
                                scaleX = state.scale.value
                                scaleY = state.scale.value
                            }
                            .drawBehind {
                                if (buffer != null) {
                                    drawContext.drawPixmap(
                                        buffer = buffer,
                                        colorFilter = bufferColorFilter
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}
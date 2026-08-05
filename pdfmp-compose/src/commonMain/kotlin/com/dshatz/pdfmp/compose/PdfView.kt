package com.dshatz.pdfmp.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.compose.platformModifier.platformScrollableModifier
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.bufferColorFilter
import com.dshatz.pdfmp.compose.tools.pageTransformModifier
import com.dshatz.pdfmp.compose.tools.toImageBitmap
import kotlinx.coroutines.launch
import kotlin.collections.set

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

    val renderedMap = remember { mutableStateMapOf<PdfTile, ConsumerBuffer>() }

    val scope = rememberCoroutineScope()
    for (tile in tiles) {
        key(tile) {
            DisposableEffect(tile) {
                val job = scope.launch {
                    val buffer = state.renderTile(tile)
                    renderedMap[tile] = buffer
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
        Box(modifier) {
            Canvas(Modifier.matchParentSize().clipToBounds()) {
                tiles.forEach { tile ->
                    renderedMap[tile]?.let { buffer ->
                        val pageOffset = currentDisplay.pageOffsetY(tile.key.page).value
                        val offset = Offset(
                            x = -currentDisplay.offsetX,
                            y = pageOffset - currentDisplay.offsetY
                        )
                        drawImage(
                            image = buffer.toImageBitmap(),
                            topLeft = Offset(tile.key.x.toFloat(), tile.key.y.toFloat()) + offset,
                            colorFilter = bufferColorFilter
                        )
                    }
                }
            }
            /*Text(
                "Tiles: ${renderedMap.size}",
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.headlineMedium
            )*/
        }
    }
}


@Composable
private fun FullPages(
    state: PdfState,
    modifier: Modifier = Modifier
) {
    val renderedMap = remember { mutableStateMapOf<Int, ConsumerBuffer>() }
    val visiblePages by state.visiblePages
    val scope = rememberCoroutineScope()
    for (page in visiblePages) {
        key(page.pageIdx) {
            DisposableEffect(page.pageIdx) {
                val job = scope.launch {
                    val buffer = state.renderFullPage(page.pageIdx)
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
    val display by state.layoutInfo()
    display?.let { currentDisplay ->
        Canvas(modifier.clipToBounds()) {
            visiblePages.forEach { page ->
                renderedMap[page.pageIdx]?.let { buffer ->
                    val pageOffset = currentDisplay.pageOffsetY(page.pageIdx).value

                    val offset = Offset(
                        x = -currentDisplay.offsetX,
                        y = pageOffset - currentDisplay.offsetY
                    )
                    val scaledWidth = state.scaledPageWidth(state.viewport, state.scale)
                    val scaledHeigh = state.scaledPageHeight(page.pageIdx, scaledWidth)
                    drawImage(
                        image = buffer.toImageBitmap(),
                        dstSize = IntSize(scaledWidth.toInt(), scaledHeigh.toInt()),
                        dstOffset = offset.round(),
                        colorFilter = bufferColorFilter
                    )
                }
            }
        }
    }
}
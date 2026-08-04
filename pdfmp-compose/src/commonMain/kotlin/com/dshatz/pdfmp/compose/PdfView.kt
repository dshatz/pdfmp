package com.dshatz.pdfmp.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.toSize
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.compose.platformModifier.platformScrollableModifier
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.compose.tools.TransformedBitmapRenderer
import com.dshatz.pdfmp.compose.tools.pageTransformModifier
import com.dshatz.pdfmp.compose.tools.toImageBitmap
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
            BaseImage(
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
                            topLeft = Offset(tile.key.x.toFloat(), tile.key.y.toFloat()) + offset
                        )
                    }
                }
            }
            Text(
                "Tiles: ${renderedMap.size}",
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun BaseImage(
    state: PdfState,
    modifier: Modifier = Modifier,
) {
    val transforms by state.produceImageTransforms()
    val uncutTransforms = transforms.map { it.uncut().copy(topGap = 0) }

    val baseImageCache = remember { mutableStateMapOf<Int, CurrentImage>() }
    val viewPortCache = remember { mutableStateOf<Size?>(null) }

    LaunchedEffect(uncutTransforms, state.viewport.value) {
        val currentViewport = state.viewport.value
        val viewportChanged = viewPortCache.value != currentViewport

        transforms.forEach { transform ->
            // Check if not already cached.
            if (!baseImageCache.containsKey(transform.pageIndex) || viewportChanged) {
                // Use the uncut version at scale 1x
                val width1x = (transform.scaledWidth / transform.scale).toInt()
                val height1x = (transform.scaledHeight / transform.scale).toInt()

                if (width1x > 0 && height1x > 0) {
                    val fullPageTransform = transform.uncut().copy(
                        scale = 1f,
                        scaledWidth = width1x,
                        scaledHeight = height1x,
                        topGap = 0
                    )

                    state.renderFullPage(fullPageTransform)?.let {
                        val (response, buffer) = it
                        val image = CurrentImage(
                            requestedTransforms = listOf(fullPageTransform),
                            loadedTransforms = response.transforms,
                            buffer = buffer
                        )
                        baseImageCache[transform.pageIndex] = image
                    }
                }
            }
        }

        if (viewportChanged) {
            viewPortCache.value = currentViewport
        }

        // remove pages that are no longer visible from cache
        val toRemove = baseImageCache.filter {
            it.key !in transforms.map { t -> t.pageIndex }
        }
        baseImageCache.keys.removeAll(toRemove.keys)
        toRemove.forEach { it.value.free() }
    }
    Column(modifier = modifier) {
        transforms.forEach { transform ->
            val cachedImage = baseImageCache[transform.pageIndex]

            val (sliceWidth, sliceHeight) = transform.sliceSize()
            val dstSize = IntSize(sliceWidth, sliceHeight)

            val density = LocalDensity.current
            val boxSize = with(density) {
                dstSize.toSize().toDpSize()
            }

            Box(
                modifier = Modifier
                    // Order matters - first padding then size!
                    // We need total occupied height to be height + padding.
                    .padding(top = with(density) { transform.topGap.toDp() })
                    .size(boxSize)
            ) {
                if (cachedImage != null) {
                    val bitmap = cachedImage.composeBitmap()
                    bitmap.touch()
                    TransformedBitmapRenderer(
                        bitmap = bitmap.imageBitmap,
                        colorFilter = bitmap.colorFilter,
                        transform = transform,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}

package com.dshatz.pdfmp.compose.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.ImageBufferPool
import com.dshatz.pdfmp.InitLib
import com.dshatz.pdfmp.PageDimensions
import com.dshatz.pdfmp.PdfData
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.TileKey
import com.dshatz.pdfmp.compose.platformModifier.rememberPlatformContext
import com.dshatz.pdfmp.d
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime


@Composable
internal fun InitLibEffect() {
    InitLib.init()
}

/** The state of a [com.dshatz.pdfmp.compose.PdfView] showing [data]. */
@Composable
fun rememberPdfState(
    data: PdfData,
    pageRange: IntRange = 0..Int.MAX_VALUE,
    pageSpacing: Dp = 0.dp
): PdfState {
    InitLibEffect()
    val context = rememberPlatformContext()
    val scope = rememberCoroutineScope()
    val pageSpacingPx = with(LocalDensity.current) { pageSpacing.toPx().toInt() }
    // One state per document: its buffer pool, renderer and render bookkeeping must not outlive the document.
    val state = remember(data) { PdfState(pageRange = pageRange, pageSpacing = pageSpacingPx, scope = scope) }
    LaunchedEffect(state) {
        val renderer = try {
            withContext(Dispatchers.Default) { data.createRenderer(context) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.failOpening(e)
            return@LaunchedEffect
        }
        state.openDocument(renderer)
    }
    return state
}

class PdfState(
    internal val pageRange: IntRange = 0..Int.MAX_VALUE,
    internal val scale: MutableState<Float> = mutableFloatStateOf(1f),
    internal val viewport: MutableState<Size> = mutableStateOf(Size(1f, 1f)),
    val pageSpacing: Int = 0,
    private val scope: CoroutineScope
) {

    /**
     * The state of the LazyColumn with page placeholders.
     *
     * This is NOT the source of truth for scroll state. See [renderingY].
     */
    private lateinit var listState: LazyListState

    /**
     * The state of the horizontal scrollable.
     *
     * This is NOT the source of truth for scroll state. See [renderingX].
     */
    private lateinit var horizontalScrollState: ScrollState

    private lateinit var bufferPool: ImageBufferPool
    internal lateinit var pages: LinkedHashMap<Int, PdfPageState>

    internal val pageRatios: SnapshotStateMap<Int, Float?> = mutableStateMapOf()

    /**
     * Source of truth for vertical scroll state.
     */
    private val renderingY = mutableFloatStateOf(0f)

    /**
     * Source of truth for horizontal scroll state.
     */
    private val renderingX = mutableFloatStateOf(0f)

    /** Extra scrollable space (px) before the first page, set by `PdfView`. */
    internal val topPadding = mutableFloatStateOf(0f)

    /** Extra scrollable space (px) after the last page, set by `PdfView`. */
    internal val bottomPadding = mutableFloatStateOf(0f)

    private val totalDocumentHeight = derivedStateOf {
        var height = (pages.size - 1) * scaledPageSpacing().toFloat() + topPadding.floatValue + bottomPadding.floatValue
        pages.values.forEach {
            height += scaledPageHeight(it.pageIdx)
        }
        height
    }

    private val coercedPageRange by derivedStateOf {
        if (isInitialized.value) {
            pages.keys.first()..pages.keys.last()
        } else {
            pageRange
        }
    }

    internal val isInitialized = mutableStateOf(false)

    private val error: MutableState<Throwable?> = mutableStateOf(null)

    /** The document could not be opened, before its renderer existed (e.g. the server was unreachable). */
    internal fun failOpening(e: Throwable) {
        error.value = e
    }

    private val _displayState = derivedStateOf {
        val error = error.value
        val initialized = isInitialized.value
        if (error != null) {
            DisplayState.Error(error)
        } else if (initialized) {
            DisplayState.Active
        } else {
            DisplayState.Initializing
        }
    }

    // Exposed state
    val displayState: State<DisplayState> = _displayState

    private val scrollState = PdfLayoutInfo(
        setOffsetY = { renderingY.value = it },
        getOffsetY = renderingY::value,
        getOffsetX = renderingX::value,
        getPageOffsetY = ::pageScrollOffset,
        getTotalHeight = { totalDocumentHeight.value - viewport.value.height },
        getVisiblePages = { visiblePages.value },
        getPageRange = ::coercedPageRange,
        getZoom = { scale.value },
        doZoom = ::zoomTowardsCenter,
        getViewportSize = { viewport.value }
    )

    /**
     * Provides a [PdfLayoutInfo] with information about current document view such as scrolling and zooming.
     */
    @Composable
    fun layoutInfo(): State<PdfLayoutInfo?> {
        return derivedStateOf {
            if (isInitialized.value) scrollState else null
        }
    }

    private fun pageScrollOffset(pageIdx: Int): Float {
        return pages.values.takeWhile {
            it.pageIdx < pageIdx
        }.fold(topPadding.floatValue) { acc, page ->
            acc + scaledPageHeight(page.pageIdx) + scaledPageSpacing()
        }
    }

    /**
     * Whether the document is loaded and bound to the [PdfView][com.dshatz.pdfmp.compose.PdfView], so it can be
     * scrolled and zoomed.
     */
    private fun isReady(): Boolean = isInitialized.value && this::listState.isInitialized

    internal fun bind(
        listState: LazyListState,
        horizontalScrollState: ScrollState,
    ) {
        this.listState = listState
        this.horizontalScrollState = horizontalScrollState
    }

    internal val visiblePages: State<List<VisiblePageInfo>> = derivedStateOf<List<VisiblePageInfo>> {
        calculateVisiblePages()
    }

    /**
     * Pages whose full-page placeholder should be rendered: the visible ones first, then one page before and after,
     * so a placeholder is usually ready by the time its page scrolls into view.
     */
    internal val pagesToRender: State<List<Int>> = derivedStateOf {
        val visible = visiblePages.value.map { it.pageIdx }
        if (visible.isEmpty()) return@derivedStateOf visible
        visible + listOf(visible.first() - 1, visible.last() + 1).filter { it !in visible && it in pages }
    }

    internal fun scaledPageWidth(viewport: MutableState<Size>, scale: State<Float>): Float {
        return viewport.value.width * scale.value
    }

    internal fun scaledPageHeight(pageIdx: Int, scaledWidth: Float = scaledPageWidth(viewport, scale)): Float {
        return scaledWidth / pageRatio(pageIdx)
    }

    /** Aspect ratio of the page. If not known yet, falls back to one of the other pages or 1. */
    private fun pageRatio(pageIdx: Int): Float {
        return pageRatios[pageIdx] ?: pageRatios.firstNotNullOfOrNull { it.value } ?: 1f
    }

    @Composable
    internal fun rememberScaledPageWidth(page: Int): State<Float> {
        return remember(page, scale.value, viewport.value) {
            derivedStateOf {
                scaledPageWidth(viewport, scale)
            }
        }
    }

    @Composable
    internal fun rememberScaledPageHeight(page: Int): State<Float> {
        val scaledWidth by rememberScaledPageWidth(page)
        return remember(page, scaledWidth, pageRatios[page]) {
            derivedStateOf {
                scaledPageHeight(page, scaledWidth)
            }
        }
    }

    @Composable
    internal fun rememberScaledPageSize(page: Int): State<DpSize> {
        val density = LocalDensity.current
        val width by rememberScaledPageWidth(page)
        val height by rememberScaledPageHeight(page)
        return derivedStateOf {
            with(density) {
                DpSize(
                    width.toDp(),
                    height.toDp()
                )
            }
        }
    }

    /**
     * Scrolls the document by [delta] pixels, clamped to its bounds.
     *
     * Positive y scrolls towards the end of the document and positive x to the right, as in
     * `ScrollState.scrollBy`. To move the document with a drag, pass the negated drag amount.
     *
     * Use this to build your own scroll or pan gestures. Does nothing until the document is shown.
     *
     * @return the part of [delta] that was scrolled, which is less than [delta] at the edges.
     */
    fun scrollBy(delta: Offset): Offset {
        if (!isReady()) return Offset.Zero
        val startX = renderingX.floatValue
        val startY = renderingY.floatValue
        onScroll(-delta)
        return Offset(renderingX.floatValue - startX, renderingY.floatValue - startY)
    }

    internal fun onScroll(delta: Offset): Offset {
        val currentX = renderingX.floatValue
        val currentY = renderingY.floatValue

        val maxX = (viewport.value.width * scale.value - viewport.value.width).coerceAtLeast(0f)
        val maxY = (totalDocumentHeight.value - viewport.value.height).coerceAtLeast(0f)

        val newX = (currentX - delta.x).coerceIn(0f, maxX)
        val newY = (currentY - delta.y).coerceIn(0f, maxY)

        val dy = newY - currentY
        val dx = newX - currentX

        renderingX.floatValue = newX
        renderingY.floatValue = newY

        listState.dispatchRawDelta(-dy)
        horizontalScrollState.dispatchRawDelta(-dx)

        return delta
    }

    /**
     * Multiplies the zoom by [zoomFactor], keeping the document point under [centroid] in place.
     *
     * The zoom stays between 1x (page width fits the view) and 5x.
     *
     * Use this to build your own zoom gestures or controls. Does nothing until the document is shown.
     *
     * @param centroid point in pixels relative to the top left corner of the [PdfView][com.dshatz.pdfmp.compose.PdfView].
     */
    fun zoomBy(zoomFactor: Float, centroid: Offset) {
        if (!isReady()) return
        val currentScale = scale.value
        val newScale = (currentScale * zoomFactor).coerceIn(1f, 5.0f)
        if (currentScale == newScale) return

        val scalingRatio = newScale / currentScale

        // The top padding doesn't scale.
        val top = topPadding.floatValue
        val mouseAbsY = renderingY.floatValue + centroid.y
        val newRenderingY = ((mouseAbsY - top) * scalingRatio + top) - centroid.y

        val mouseAbsX = renderingX.floatValue + centroid.x
        val newRenderingX = if (newScale == 1f) 0f else (mouseAbsX * scalingRatio) - centroid.x

        scale.value = newScale
        renderingY.floatValue = newRenderingY.coerceAtLeast(0f)
        renderingX.floatValue = newRenderingX.coerceAtLeast(0f)

        updateUiScrollPosition(newRenderingX.coerceAtLeast(0f), newRenderingY.coerceAtLeast(0f), newScale)
    }

    internal fun zoomTowardsCenter(zoomFactor: Float) {
        val center = Offset(viewport.value.width / 2, viewport.value.height / 2)
        zoomBy(zoomFactor / scale.value, center)
    }

    private fun updateUiScrollPosition(x: Float, y: Float, s: Float) {
        val (targetIndex, targetOffset) = getPageAndOffsetForAbsoluteY(y, s)
        scope.launch {
            listState.scrollToItem(targetIndex, targetOffset)
            horizontalScrollState.scrollTo(x.toInt())
        }
    }

    private fun calculateVisibleTiles(): List<PdfTile> {
        val currentY = renderingY.floatValue
        val currentX = renderingX.floatValue
        val viewportHeight = viewport.value.height
        val viewportWidth = viewport.value.width
        val currentScale = scale.value
        val currentScaledWidth = viewportWidth * currentScale

        val viewportBottom = currentY + viewportHeight
        val viewportRight = currentX + viewportWidth
        // Tiles around the viewport are rendered ahead, so they are ready when they scroll in or after zooming out.
        val areaTop = currentY - TILE_PREFETCH_MARGIN
        val areaBottom = viewportBottom + TILE_PREFETCH_MARGIN
        val areaLeft = currentX - TILE_PREFETCH_MARGIN_HORIZONTAL
        val areaRight = viewportRight + TILE_PREFETCH_MARGIN_HORIZONTAL

        val verticalSpacing = scaledPageSpacing()
        val visibleTiles = mutableListOf<PdfTile>()
        val prefetchTiles = mutableListOf<PdfTile>()
        var accumulatedHeight = topPadding.floatValue

        for ((i, _) in pages) {
            val pageHeight = scaledPageHeight(i, currentScaledWidth)
            val pageTop = accumulatedHeight
            val pageBottom = pageTop + pageHeight
            if (pageBottom > areaTop && pageTop < areaBottom) {

                val topLeft = Offset(
                    x = areaLeft.coerceAtLeast(0f),
                    y = (areaTop - pageTop).coerceAtLeast(0f),
                )
                val bottomRight = Offset(
                    x = areaRight.coerceAtMost(currentScaledWidth),
                    y = (areaBottom - pageTop).coerceAtMost(pageHeight)
                )

                val minRow = (topLeft.y / PdfTile.HEIGHT).toInt()
                val maxRow = (bottomRight.y / PdfTile.HEIGHT).ceilToInt()
                val minCol = (topLeft.x / PdfTile.WIDTH).toInt()
                val maxCol = (bottomRight.x / PdfTile.WIDTH).ceilToInt()

                val scaledDimensions = PageDimensions(
                    currentScaledWidth.toInt(),
                    pageHeight.toInt()
                )

                for (row in minRow..<maxRow) {
                    for(col in minCol..<maxCol) {
                        val tile = PdfTile(
                            key = TileKey(
                                i,
                                col * PdfTile.WIDTH,
                                row * PdfTile.HEIGHT
                            ),
                            scaledPage = scaledDimensions
                        )
                        val tileTop = pageTop + row * PdfTile.HEIGHT
                        val tileLeft = col * PdfTile.WIDTH
                        val onScreen = tileTop < viewportBottom && tileTop + PdfTile.HEIGHT > currentY &&
                                tileLeft < viewportRight && tileLeft + PdfTile.WIDTH > currentX
                        if (onScreen) visibleTiles += tile else prefetchTiles += tile
                    }
                }
            }

            accumulatedHeight += pageHeight + verticalSpacing
            if (accumulatedHeight > areaBottom) break
        }
        // On-screen tiles first: renders start in this order.
        return visibleTiles + prefetchTiles
    }

    private fun Float.ceilToInt(): Int {
        return ceil(this).toInt()
    }

    private fun calculateVisiblePages(): List<VisiblePageInfo> {
        val currentY = renderingY.floatValue
        val currentX = renderingX.floatValue
        val viewportHeight = viewport.value.height
        val viewportWidth = viewport.value.width
        val currentScale = scale.value
        val currentScaledWidth = viewportWidth * currentScale

        val verticalSpacing = scaledPageSpacing()

        val visiblePages = mutableListOf<VisiblePageInfo>()
        var accumulatedHeight = topPadding.floatValue

        for ((i, _) in pages) {
            val pageHeight = scaledPageHeight(i, currentScaledWidth)
            val pageTop = accumulatedHeight
            val pageBottom = pageTop + pageHeight

            if (pageBottom > currentY && pageTop < currentY + viewportHeight) {
                val topCutoff = (currentY - pageTop).coerceAtLeast(0f)
                val bottomCutoff = (pageBottom - (currentY + viewportHeight)).coerceAtLeast(0f)
                val leftCutoff = currentX.coerceIn(0f, currentScaledWidth)
                val rightCutoff = (currentScaledWidth - (currentX + viewportWidth)).coerceAtLeast(0f)

                val topGap = if (visiblePages.isEmpty()) (pageTop - currentY).coerceAtLeast(0f).toInt()
                else verticalSpacing

                visiblePages.add(
                    VisiblePageInfo(
                        pageIdx = i,
                        topCutoff = topCutoff,
                        bottomCutoff = bottomCutoff,
                        leftCutoff = leftCutoff.toInt(),
                        rightCutoff = rightCutoff.toInt(),
                        scaledWidth = currentScaledWidth,
                        scaledHeight = pageHeight,
                        topGap = topGap
                    )
                )
            }
            accumulatedHeight += pageHeight + verticalSpacing
            if (accumulatedHeight > currentY + viewportHeight) break
        }
        return visiblePages
    }

    private fun getPageAndOffsetForAbsoluteY(absY: Float, s: Float): Pair<Int, Int> {
        var acc = topPadding.floatValue
        val w = viewport.value.width * s
        val spacing = scaledPageSpacing(s).toFloat()

        if (absY <= acc) return pages.keys.first() to 0
        for (i in pages.keys) {
            val h = scaledPageHeight(i, w)
            if (acc + h + spacing > absY) {
                val offset = (absY - acc).toInt()
                return i to minOf(offset, h.toInt())
            }
            acc += h + spacing
        }
        return 0 to 0
    }

    internal fun scaledPageSpacing(scale: Float = this.scale.value): Int {
        return (pageSpacing * scale).toInt()
    }

    internal suspend fun renderTile(tile: PdfTile): ImageBuffer {
        pendingTileRenders.update { it + 1 }
        try {
            if (tile.key.page !in pageRatios) {
                pageRatios[tile.key.page] = renderer!!.getPageRatio(tile.key.page)
            }
            // Placeholders of visible pages first: the worker handles requests in order, so a page render queued
            // behind dozens of tile renders would leave the page blank for a long time.
            pendingFullPageRenders.first { it == 0 }
            val (buffer, needsRender) = bufferPool.getBufferTile(tile)
            if (needsRender) {
                try {
                    measureTime {
                        renderToCompletion { bufferPool.withRendering(buffer) { renderer!!.renderTile(tile, buffer) } }
                    }.also { d("Tile rendered in ${it.absoluteValue}: $tile") }
                } catch (e: Throwable) {
                    // A fresh buffer that was never displayed: nothing else would free it.
                    bufferPool.freeTileBuffer(tile)
                    throw e
                }
                // New pixels: drop the cached image so they get uploaded. Only here, so a tile that is still
                // rendered (e.g. when scrolling back) keeps its texture instead of being uploaded again.
                buffer.reset()
            }
            return buffer
        } finally {
            pendingTileRenders.update { it - 1 }
        }
    }

    /**
     * Runs a render to completion even if the caller is cancelled: a cancelled native suspend call returns right away
     * while native code keeps writing into the buffer.
     */
    private suspend fun renderToCompletion(block: suspend () -> Unit) {
        activeRenders.update { it + 1 }
        try {
            // Checked after registering: closing the document sets it before waiting for activeRenders.
            if (closing) throw CancellationException("Document is closing")
            withContext(NonCancellable) { block() }
        } finally {
            activeRenders.update { it - 1 }
        }
    }

    /** The renderer and the buffer pool are only closed once this is 0. */
    private val activeRenders = MutableStateFlow(0)

    @Volatile
    private var closing = false

    /** Tile renders started and not finished yet. Prefetched full pages wait until this is 0. */
    private val pendingTileRenders = MutableStateFlow(0)

    internal fun freeTile(tile: PdfTile) {
        bufferPool.freeTileBuffer(tile)
    }

    /** Whether [buffer] is still the pool's buffer for [tile], i.e. the tile wasn't freed since it was rendered. */
    internal fun ownsTileBuffer(tile: PdfTile, buffer: ImageBuffer): Boolean = bufferPool.tileBuffer(tile) === buffer

    internal fun freePage(page: Int) {
        bufferPool.freePageBuffer(page)
    }

    /**
     * Full-page renders run one at a time. Pages that only flash by during fast scrolling get cancelled while
     * waiting here, before a page-sized buffer is allocated for them.
     */
    private val fullPageRenderMutex = Mutex()

    /** Visible full-page renders running or waiting for [fullPageRenderMutex]. Tile renders wait until this is 0. */
    private val pendingFullPageRenders = MutableStateFlow(0)

    /** On-screen size of a full page at scale 1. The page buffer itself is [FULL_PAGE_RESOLUTION] times smaller. */
    internal fun fullPageSize(page: Int): IntSize {
        val pageWidth = viewport.value.width
        return IntSize(pageWidth.toInt(), (pageWidth / pageRatio(page)).toInt())
    }

    /**
     * Renders the full-page placeholder of [page]. Visible pages go before tiles. A [prefetch] (a page next to the
     * visible ones) is only needed later, so it waits for the visible tiles instead.
     */
    internal suspend fun renderFullPage(page: Int, prefetch: Boolean): ImageBuffer {
        if (prefetch) {
            // Longer than the tile debounce, so the visible tiles have started by the time we check.
            delay(PREFETCH_DELAY)
            pendingTileRenders.first { it == 0 }
            return renderFullPageInOrder(page)
        }
        pendingFullPageRenders.update { it + 1 }
        try {
            return renderFullPageInOrder(page)
        } finally {
            pendingFullPageRenders.update { it - 1 }
        }
    }

    private suspend fun renderFullPageInOrder(page: Int): ImageBuffer {
        if (page !in pageRatios) pageRatios[page] = renderer!!.getPageRatio(page)

        return fullPageRenderMutex.withLock {
            val pageSize = fullPageSize(page)
            // Full pages are only placeholders until the tiles covering them are rendered, so a lower resolution
            // is enough. It also keeps page-sized buffers small, in pdfium and in the Skiko heap.
            val pageDimensions = PageDimensions(
                (pageSize.width * FULL_PAGE_RESOLUTION).toInt(),
                (pageSize.height * FULL_PAGE_RESOLUTION).toInt()
            )
            val (buffer, needsRender) = bufferPool.getBufferPage(page, pageDimensions)

            if (needsRender) {
                renderToCompletion {
                    bufferPool.withRendering(buffer) {
                        renderer!!.render(
                            page,
                            pageDimensions,
                            buffer
                        )
                    }
                }
                // The buffer may have held another page: drop its cached image so the new pixels are drawn instead
                // of a texture cached for the old contents. Only after a render, so unchanged pages aren't re-uploaded.
                buffer.reset()
            }
            buffer
        }
    }

    internal val visibleTiles = derivedStateOf {
        if (isInitialized.value) {
            calculateVisibleTiles()
        } else emptyList()
    }

    internal suspend fun initPages(renderer: PdfRenderer): Result<Unit> {
        return runCatching { renderer.getPageCount() }.mapCatching { count ->
            val range = (0..<count)
            d("initPages: page range: $range, limit: $pageRange")
            val truncated = range.withIndex()
                .drop(pageRange.first).take(min(pageRange.last - pageRange.first, count - pageRange.first) + 1)
            val map = linkedMapOf<Int, PdfPageState>()
            truncated.forEach { (pageIdx, _) ->
                map[pageIdx] = PdfPageState(
                    pageIdx,
                )
            }
            pages = map
        }
    }

    private var renderer: PdfRenderer? = null
    internal suspend fun openDocument(renderer: PdfRenderer) {
        this.renderer = renderer
        closing = false
        // Created first, so finally can close it even if opening is cancelled.
        bufferPool = ImageBufferPool()
        try {
            runCatching { renderer.openDocument().getOrThrow() }.onFailure {
                d("Failed to open document: $it")
                this@PdfState.error.value = it
            }.onSuccess {
                error.value = null
                initPages(renderer).getOrThrow()
                isInitialized.value = true
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            d("Closing renderer")
            isInitialized.value = false
            withContext(NonCancellable) {
                closing = true
                activeRenders.first { it == 0 }
                renderer.close()
                bufferPool.close()
            }
        }
    }

    internal fun setViewport(size: Size) {
        val ratio = size.width / viewport.value.width
        viewport.value = size
        if (!isInitialized.value || ratio == 1f) return
        // Page heights follow the width, so scale the scroll position like zoomBy does (top padding doesn't scale).
        val top = topPadding.floatValue
        renderingY.floatValue = ((renderingY.floatValue - top) * ratio + top).coerceAtLeast(0f)
        renderingX.floatValue *= ratio
        updateUiScrollPosition(renderingX.floatValue, renderingY.floatValue, scale.value)
    }
}

data class VisiblePageInfo(
    val pageIdx: Int,
    /**
     * How much of the page (px) is above the top of the viewport.
     */
    val topCutoff: Float = 0f,
    /**
     * How much of the page (px) is below the bottom of the viewport.
     */
    val bottomCutoff: Float = 0f,
    /**
     * How much of the page (px) is more left than the left edge of the viewport.
     */
    val leftCutoff: Int = 0,
    /**
     * How much of the page (px) is more right than the right edge of the viewport.
     */
    val rightCutoff: Int = 0,
    /**
     * Scaled width of the page in pixels. Can be wider than viewport.
     */
    val scaledWidth: Float,
    /**
     * Scaled height of the page in pixels. Can be higher than viewport.
     */
    val scaledHeight: Float,
    /**
     * Distance from viewport top (0 if page overlaps top edge)
     */
    val topGap: Int = 0
) {
    init {
        if (scaledHeight.toInt() - topCutoff.toInt() - bottomCutoff.toInt() < 0) error("Invalid parameters $this")
    }

    /**
     * How much of the page height fits within the viewport.
     */
    val visibilityPercentageH = (scaledHeight - topCutoff - bottomCutoff) / scaledHeight

    /**
     * How much of the page width fits within the viewport.
     */
    val visibilityPercentageW = (scaledWidth - leftCutoff - rightCutoff) / scaledWidth
}
/** How far above and below the viewport tiles are rendered ahead. */
private const val TILE_PREFETCH_MARGIN = 2f * PdfTile.HEIGHT

/** How far left and right of the viewport tiles are rendered ahead. Less than vertically: zooming is slower than scrolling. */
private const val TILE_PREFETCH_MARGIN_HORIZONTAL = 1f * PdfTile.WIDTH

/** How long a prefetched full page waits before rendering, see [PdfState.renderFullPage]. */
private val PREFETCH_DELAY = 300.milliseconds

/**
 * Resolution of full-page placeholders relative to their on-screen size (0.5 = half the width and height).
 * Placeholders show only until the tiles covering them arrive; small ones are cheap to render, to keep in memory and
 * to upload to the GPU when their page scrolls into view.
 */
internal const val FULL_PAGE_RESOLUTION = 0.33f

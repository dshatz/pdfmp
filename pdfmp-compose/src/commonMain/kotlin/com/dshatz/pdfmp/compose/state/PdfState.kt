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
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.ConsumerBufferPool
import com.dshatz.pdfmp.InitLib
import com.dshatz.pdfmp.PageDimensions
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.PdfTile
import com.dshatz.pdfmp.TileKey
import com.dshatz.pdfmp.d
import com.dshatz.pdfmp.getPageCountSuspend
import com.dshatz.pdfmp.getPageRatioSuspend
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.renderSuspend
import com.dshatz.pdfmp.renderTileSuspend
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.time.ExperimentalTime


@Composable
fun InitLibEffect() {
    InitLib.init()
}

@Composable
fun rememberPdfState(
    pdfSource: PdfSource,
    pageRange: IntRange = 0..Int.MAX_VALUE,
    pageSpacing: Dp = 0.dp
): PdfState {
    InitLibEffect()
    val scope = rememberCoroutineScope()
    val pageSpacingPx = with(LocalDensity.current) { pageSpacing.toPx().toInt() }
    val state = remember { PdfState(pageRange = pageRange, pageSpacing = pageSpacingPx, scope = scope) }
    LaunchedEffect(pdfSource) {
        val renderer = withContext(Dispatchers.Default) {
            PdfRenderer(pdfSource)
        }
        state.openDocument(renderer)
    }
    return state
}

@Composable
fun rememberPdfState(
    customPdfSourceAdapter: CustomPdfSourceAdapter,
    pageRange: IntRange = 0..Int.MAX_VALUE,
    pageSpacing: Dp = 0.dp
): PdfState {
    InitLibEffect()
    val scope = rememberCoroutineScope()
    val pageSpacingPx = with(LocalDensity.current) { pageSpacing.toPx().toInt() }
    val state = remember { PdfState(pageRange = pageRange, pageSpacing = pageSpacingPx, scope = scope) }
    LaunchedEffect(customPdfSourceAdapter) {
        val renderer = withContext(Dispatchers.Default) {
            d("Init with custom source (jvm)")
            PdfRenderer(customPdfSourceAdapter).also {
                d("After init with custom source (jvm)")
            }
        }
        state.openDocument(renderer)
    }
    return state
}

@OptIn(ExperimentalTime::class)
data class PdfState(
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

    private lateinit var bufferPool: ConsumerBufferPool
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

    private val totalDocumentHeight = derivedStateOf {
        var height = (pages.size - 1) * scaledPageSpacing().toFloat()
        pages.values.forEach {
            height += scaledPageHeight(it.pageIdx)
        }
        height
    }

    private val coercedPageRange by derivedStateOf {
        if (isInitialized.value) {
            val pageIdxs = pages.map { it.value.pageIdx }
            pageIdxs.min()..pageIdxs.max()
        } else {
            pageRange
        }
    }

    internal val isInitialized = mutableStateOf(false)

    private val error: MutableState<Throwable?> = mutableStateOf(null)

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
        }.fold(0f) { acc, page ->
            acc + scaledPageHeight(page.pageIdx) + scaledPageSpacing()
        }
    }

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

    internal fun scaledPageWidth(viewport: MutableState<Size>, scale: State<Float>): Float {
        return viewport.value.width * scale.value
    }

    internal fun scaledPageHeight(pageIdx: Int, scaledWidth: Float = scaledPageWidth(viewport, scale)): Float {
        // If aspect ratio is not known yet, fallback to one of the other pages or A4.
        val aspectRatio = pageRatios[pageIdx] ?: pageRatios.firstNotNullOfOrNull { it.value } ?: 1f
        return scaledWidth / (aspectRatio)
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

    private val horizontalScrollOffset = mutableStateOf(0)

    internal fun reportHorizontalOffset(offset: Int) {
        horizontalScrollOffset.value = offset
    }

    internal fun onScroll(delta: Offset): Offset {
        val currentX = renderingX.floatValue
        val currentY = renderingY.floatValue

        val maxX = (viewport.value.width * scale.value - viewport.value.width).coerceAtLeast(0f)
        var totalContentHeight = 0f
        for (i in pages.keys) {
            totalContentHeight += scaledPageHeight(i)
        }

        //Account for page spacings
        totalContentHeight += scaledPageSpacing() * (pages.size - 1)

        val maxY = (totalContentHeight - viewport.value.height).coerceAtLeast(0f)

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

    internal fun zoomBy(zoomFactor: Float, centroid: Offset) {
        val currentScale = scale.value
        val newScale = (currentScale * zoomFactor).coerceIn(1f, 5.0f)
        if (currentScale == newScale) return

        val scalingRatio = newScale / currentScale

        val mouseAbsY = renderingY.floatValue + centroid.y
        val newRenderingY = (mouseAbsY * scalingRatio) - centroid.y

        val mouseAbsX = renderingX.floatValue + centroid.x
        val newRenderingX = if (newScale == 1f) 0f else (mouseAbsX * scalingRatio) - centroid.x

        scale.value = newScale
        renderingY.floatValue = newRenderingY.coerceAtLeast(0f)
        renderingX.floatValue = newRenderingX.coerceAtLeast(0f)
        reportHorizontalOffset(newRenderingX.toInt())

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

        val verticalSpacing = scaledPageSpacing()
        val visibleTiles = mutableListOf<PdfTile>()
        var accumulatedHeight = 0f

        for ((i, _) in pages) {
            val pageHeight = scaledPageHeight(i, currentScaledWidth)
            val pageTop = accumulatedHeight
            val pageBottom = pageTop + pageHeight
            if (pageBottom > currentY && pageTop < viewportBottom) {

                val topLeft = Offset(
                    x = currentX.coerceAtLeast(0f),
                    y = (currentY - pageTop).coerceAtLeast(0f),
                )
                val bottomRight = Offset(
                    x = viewportRight.coerceAtMost(currentScaledWidth),
                    y = (viewportBottom - pageTop).coerceAtMost(pageHeight)
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
                        visibleTiles += tile
                    }
                }
            }

            accumulatedHeight += pageHeight + verticalSpacing
            if (accumulatedHeight > currentY + viewportHeight) break
        }
        return visibleTiles
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
        var accumulatedHeight = 0f

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
        var acc = 0f
        val w = viewport.value.width * s
        val spacing = pageSpacing.toFloat()

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

    internal fun scaledPageSpacing(): Int {
        return (pageSpacing * scale.value).toInt()
    }

    internal suspend fun renderTile(tile: PdfTile): ConsumerBuffer = withContext(pdfiumDispatcher) {
        val (buffer, needsRender) = bufferPool.getBufferTile(tile)
        if (needsRender) {
            buffer.withAddress {
                renderer!!.renderTileSuspend(tile, BufferInfo(buffer.dimensions, it))
            }
        }
        return@withContext buffer
    }

    internal fun freeTile(tile: PdfTile) {
        bufferPool.freeTileBuffer(tile)
    }

    internal fun freePage(page: Int) {
        bufferPool.freePageBuffer(page)
    }

    private val pdfiumDispatcher = Dispatchers.Default.limitedParallelism(1)

    internal suspend fun renderFullPage(page: Int): ConsumerBuffer = withContext(pdfiumDispatcher) {
        pageRatios[page] = renderer!!.getPageRatioSuspend(page).getOrThrow()

        val pageWidth = scaledPageWidth(viewport, scale)
        val pageHeight = scaledPageHeight(page, pageWidth)
        val pageDimensions = PageDimensions(pageWidth.toInt(), pageHeight.toInt())
        val buffer = bufferPool.getBufferPage(page, pageDimensions)

        buffer.withAddress {
            renderer!!.renderSuspend(
                RenderRequest(
                    page,
                    pageDimensions,
                    BufferInfo(buffer.dimensions, it)
                )
            )
        }
        buffer
    }

    /*@Composable
    internal fun produceImageTransforms(): State<List<PageTransform>> {
        return derivedStateOf {
            visiblePages.value.map {
                PageTransform(
                    pageIndex = it.pageIdx,
                    topCutoff = it.topCutoff.toInt(),
                    bottomCutoff = it.bottomCutoff.toInt(),
                    leftCutoff = it.leftCutoff,
                    rightCutoff = it.rightCutoff,
                    scaledWidth = it.scaledWidth.toInt(),
                    scaledHeight = it.scaledHeight.toInt(),
                    topGap = it.topGap,
                    scale = scale.value
                )
            }
        }
    }*/

    internal val visibleTiles = derivedStateOf {
        if (isInitialized.value) {
            calculateVisibleTiles()
        } else emptyList()
    }

    internal suspend fun initPages(renderer: PdfRenderer): Result<Unit> {
        return renderer.getPageCountSuspend().mapCatching { count ->
            val range = (0..<count)
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
        try {
            withContext(pdfiumDispatcher) {
                d("openDocument jvm")
                renderer.openDocument().mapCatching {
                    d("after openDocument jvm")
                    bufferPool = ConsumerBufferPool()
                }.onFailure {
                    d("Failed to open document: $it")
                    this@PdfState.error.value = it
                }.onSuccess {
                    error.value = null
                    initPages(renderer).getOrThrow()
                    isInitialized.value = true
                }
                awaitCancellation()
            }
        } finally {
            d("Closing renderer")
            isInitialized.value = false
            withContext(NonCancellable + pdfiumDispatcher) {
                renderer.close()
            }
        }
    }

    internal fun setViewport(size: Size) {
        viewport.value = size
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
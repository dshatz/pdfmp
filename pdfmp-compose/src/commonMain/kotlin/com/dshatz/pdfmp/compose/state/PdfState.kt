package com.dshatz.pdfmp.compose.state

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.dshatz.pdfmp.*
import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.ExperimentalTime

@Composable
fun rememberPdfState(
    pdfSource: PdfSource,
    pageRange: IntRange = 0..Int.MAX_VALUE,
    pageSpacing: Dp = 0.dp
): PdfState {
    val scope = rememberCoroutineScope()
    val pageSpacingPx = with(LocalDensity.current) { pageSpacing.toPx().toInt() }
    val state = remember { PdfState(pdfSource, pageRange = pageRange, pageSpacing = pageSpacingPx, scope = scope) }
    state.OpenDisposableDocument()
    return state
}

@OptIn(ExperimentalTime::class)
data class PdfState(
    val pdfSource: PdfSource,
    internal val pageRange: IntRange = 0..Int.MAX_VALUE,
    private val scale: MutableState<Float> = mutableFloatStateOf(1f),
    internal val viewport: MutableState<Size> = mutableStateOf(Size(1f, 1f)),
    val pageSpacing: Int = 0,
    private val scope: CoroutineScope
) {
    private lateinit var renderer: PdfRenderer


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
        getPageOffsetY = ::pageScrollOffset,
        getTotalHeight = { totalDocumentHeight.value - viewport.value.height },
        getVisiblePages = { visiblePages.value },
        getPageRange = ::coercedPageRange,
        getZoom = { scale.value },
        doZoom = ::zoomTowardsCenter
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

    private fun scaledPageWidth(viewport: MutableState<Size>, scale: State<Float>): Float {
        return viewport.value.width * scale.value
    }

    private fun scaledPageHeight(pageIdx: Int, scaledWidth: Float = scaledPageWidth(viewport, scale)): Float {
        return scaledWidth / (pages[pageIdx]!!.aspectRatio)
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
        return remember(page, scaledWidth) {
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

        renderingX.floatValue = newX
        renderingY.floatValue = newY
        reportHorizontalOffset(newX.toInt())

        updateUiScrollPosition(newX, newY, scale.value)

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

    internal fun renderViewport(transforms: List<PageTransform>): Pair<RenderResponse, ConsumerBuffer>? {
        // We can just grab the offset of the first visible page.
        // Subsequent pages are automatically spaced by the native renderer.
        val topOffset = visiblePages.value.firstOrNull()?.topGap ?: 0

        // Otherwise native will receive 0x0 size and crash.
        if (transforms.isEmpty()) return null

        val buffer = bufferPool.getBufferViewport(transforms)
        return buffer.withAddress {
            val response = renderer.render(
                RenderRequest(
                    transforms,
                    pageSpacing,
                    topOffset,
                    buffer.dimensions.withAddress(it),
                )
            )
            response.map { resp ->
                resp to buffer
            }.onFailure { error ->
                this.error.value = error
                e("Failed to render viewport", error)
            }.getOrNull()
        }
    }

    internal fun renderFullPage(transform: PageTransform): Pair<RenderResponse, ConsumerBuffer>? {
        val buffer = bufferPool.getBufferPage(transform)
        return buffer.withAddress {
            val response = renderer.render(
                RenderRequest(
                    listOf(transform),
                    0,
                    0,
                    buffer.dimensions.withAddress(it)
                )
            )
            response.map { resp ->
                resp to buffer
            }.onFailure { error ->
                this.error.value = error
                e("Failed to render pages", error)
            }.getOrNull()
        }
    }

    @Composable
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
    }

    internal fun initPages(renderer: PdfRenderer): Result<Unit> {
        return renderer.getPageRatios().mapCatching { allRatios ->
            val truncated = allRatios.withIndex()
                .drop(pageRange.first).take(min(pageRange.last - pageRange.first, allRatios.size - pageRange.first) + 1)
            val map = linkedMapOf<Int, PdfPageState>()
            truncated.forEach { (pageIdx, ratio) ->
                map[pageIdx] = PdfPageState(
                    pageIdx,
                    aspectRatio = ratio
                )
            }
            pages = map
        }
    }

    @Composable
    internal fun OpenDisposableDocument() {
        InitLib().init()
        DisposableEffect(pdfSource) {
            val rendererResult = PdfRendererFactory.createFromSource(pdfSource)
            rendererResult.mapCatching { renderer ->
                this@PdfState.renderer = renderer
                bufferPool = ConsumerBufferPool()
            }.onFailure {
                d("Failed to open document: $it")
                this@PdfState.error.value = it
            }.onSuccess {
                initPages(renderer).getOrThrow()
                isInitialized.value = true
            }

            onDispose {
                isInitialized.value = false
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
    val topCutoff: Float,
    /**
     * How much of the page (px) is below the bottom of the viewport.
     */
    val bottomCutoff: Float,
    /**
     * How much of the page (px) is more left than the left edge of the viewport.
     */
    val leftCutoff: Int,
    /**
     * How much of the page (px) is more right than the right edge of the viewport.
     */
    val rightCutoff: Int,
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
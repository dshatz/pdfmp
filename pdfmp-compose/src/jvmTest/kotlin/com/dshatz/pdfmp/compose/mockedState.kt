package com.dshatz.pdfmp.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.state.PdfState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A [PdfState] of [pageCount] pages of [pageRatio] (width / height), laid out in [viewportSize]. */
suspend fun getMockedState(
    pageRatio: Float = 1f,
    pageCount: Int = 10,
    gap: Int = 0,
    viewportSize: Size = Size(1000f, 1500f),
): PdfState {
    val renderer = mockk<PdfRenderer> {
        coEvery { getPageCount() } returns pageCount
        coEvery { getPageRatio(any()) } returns pageRatio
    }
    val state = PdfState(pageSpacing = gap, scope = CoroutineScope(Dispatchers.Default))
    state.initPages(renderer).getOrThrow()
    repeat(pageCount) { state.pageRatios[it] = pageRatio }
    state.bind(LazyListState(0, 0), ScrollState(0))
    state.isInitialized.value = true
    state.setViewport(viewportSize)
    return state
}

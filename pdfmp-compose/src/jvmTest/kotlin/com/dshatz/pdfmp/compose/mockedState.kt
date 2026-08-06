package com.dshatz.pdfmp.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.state.PdfState
import com.dshatz.pdfmp.source.PdfSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path

suspend fun getMockedState(
    pageRatio: Float = 1f,
    pageCount: Int = 10,
    gap: Int = 0,
    viewportSize: Size = Size(1000f, 1500f),
): PdfState {
    InitLib().init()
    val scope = CoroutineScope(Dispatchers.Default)
    val renderer = mockk<PdfRenderer>() {
        coEvery { getPageRatio(any(), any()) } answers {
            val callback = secondArg<PdfOperationCallback>()
            callback.onPageRatio(Result.success(pageRatio))
        }
        coEvery { getPageCount(any()) } answers {
            val callback = firstArg<PdfOperationCallback>()
            callback.onPageCount(Result.success(pageCount))
        }
    }
    val state = PdfState(PdfRenderer(PdfSource.PdfPath(Path(""))), pageSpacing = gap, scope = scope)
    state.initPages(renderer)
    state.bind(LazyListState(0, 0), ScrollState(0))

    state.isInitialized.value = true

    state.setViewport(viewportSize)
    return state
}
package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import com.dshatz.pdfmp.compose.state.PdfLayoutInfo

@Composable
fun PdfLayoutInfo.rememberScrollbarAdapter(): ScrollbarAdapter {
    return PdfScrollbarAdapter(this)
}

internal class PdfScrollbarAdapter(private val layoutInfo: PdfLayoutInfo): ScrollbarAdapter {
    override val scrollOffset: Double get() = layoutInfo.offsetY.toDouble()
    override val contentSize: Double get() = layoutInfo.documentHeight.value.toDouble() + layoutInfo.viewportSize.value.height

    override val viewportSize: Double get() = layoutInfo.viewportSize.value.height.toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        layoutInfo.offsetY = scrollOffset.toFloat()
    }
}
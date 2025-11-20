package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.Modifier
import com.dshatz.pdfmp.compose.state.PdfPageState

expect fun Modifier.pageTransformModifier(pageState: PdfPageState, enablePan: Boolean): Modifier
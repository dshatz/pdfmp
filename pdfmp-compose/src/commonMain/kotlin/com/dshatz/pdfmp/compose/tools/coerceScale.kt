package com.dshatz.pdfmp.compose.tools

internal fun Float.coerceScale(): Float = coerceIn(1f, 5f)
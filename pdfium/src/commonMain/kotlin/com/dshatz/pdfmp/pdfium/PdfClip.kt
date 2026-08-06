package com.dshatz.pdfmp.pdfium

import kotlinx.serialization.Serializable

@Serializable
data class PdfClip(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
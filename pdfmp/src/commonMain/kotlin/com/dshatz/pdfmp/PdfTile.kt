package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniSerializable

@JniSerializable
data class PdfTile(
    val key: TileKey,
    val scaledPage: PageDimensions
) {
    companion object {
        const val HEIGHT = 256
        const val WIDTH = 256
    }
}

@JniSerializable
data class TileKey(
    val page: Int,
    val x: Int,
    val y: Int
)

@JniSerializable
data class PageDimensions(
    val width: Int,
    val height: Int
)
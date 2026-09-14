package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniSerializable
import kotlinx.serialization.Serializable

@JniSerializable
@Serializable
data class PdfTile(
    val key: TileKey,
    val scaledPage: PageDimensions
) {
    companion object {
        const val HEIGHT = 256
        const val WIDTH = 256
    }
}

@Serializable
@JniSerializable
data class TileKey(
    val page: Int,
    val x: Int,
    val y: Int
)

@Serializable
@JniSerializable
data class PageDimensions(
    val width: Int,
    val height: Int
)
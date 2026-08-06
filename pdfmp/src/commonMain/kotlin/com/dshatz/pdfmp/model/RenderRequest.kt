package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import com.dshatz.pdfmp.PageDimensions

@JniSerializable
data class RenderRequest(
    val page: Int,
    val dimensions: PageDimensions,
    val bufferInfo: BufferInfo,
)

@JniSerializable
data class BufferInfo(
    val width: Int,
    val height: Int,
    val stride: Int
)
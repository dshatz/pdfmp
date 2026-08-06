package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniSerializable

@JniSerializable
data class SkBitmapInfo(
    val ptr: Long,
    val width: Int,
    val height: Int,
    val strideBytes: Int,
)
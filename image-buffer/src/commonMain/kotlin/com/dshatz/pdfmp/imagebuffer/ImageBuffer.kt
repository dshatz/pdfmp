package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter


@JniAdapter(ImageBufferAdapter::class)
expect class ImageBuffer: IImageBuffer {
    override val width: Int
    override val height: Int
    override val stride: Int
    override fun reset()
}

expect object ImageBufferAdapter

interface IImageBuffer {
    val width: Int
    val height: Int
    val stride: Int

    fun reset()
}
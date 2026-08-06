package com.dshatz.pdfmp.imagebuffer


expect class ImageBuffer: IImageBuffer {
    override val width: Int
    override val height: Int
    override val stride: Int
}

interface IImageBuffer {
    val width: Int
    val height: Int
    val stride: Int
}
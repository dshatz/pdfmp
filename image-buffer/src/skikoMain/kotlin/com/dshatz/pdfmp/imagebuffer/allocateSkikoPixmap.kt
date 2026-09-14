package com.dshatz.pdfmp.imagebuffer

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap

fun allocateSkikoPixmap(
    width: Int,
    height: Int
): Pixmap {
    val imageInfo = ImageInfo(
        ColorInfo(
            ColorType.BGRA_8888,
            ColorAlphaType.PREMUL,
            colorSpace = null
        ),
        width,
        height
    )
    val strideBytes = width * 4
    val pixmap = Pixmap.make(
        imageInfo,
        Data.makeUninitialized(strideBytes * height),
        strideBytes
    )
    return pixmap
}
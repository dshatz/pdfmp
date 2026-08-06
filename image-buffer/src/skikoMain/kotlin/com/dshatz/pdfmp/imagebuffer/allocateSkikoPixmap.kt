package com.dshatz.pdfmp.imagebuffer

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap

fun allocateSkikoPixmap(
    width: Int,
    height: Int
): Pixmap {
    val bitmap = Bitmap().also {
        it.allocPixels(
            ImageInfo(
                ColorInfo(
                    ColorType.BGRA_8888,
                    ColorAlphaType.PREMUL,
                    colorSpace = null
                ),
                width,
                height
            )
        )
    }
    return bitmap.peekPixels()!!
}
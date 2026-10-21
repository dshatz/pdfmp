package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap

@JniAdapter(adapter = ImageBufferAdapter::class)
actual data class ImageBuffer(
    actual override val width: Int,
    actual override val height: Int,
    actual override val stride: Int,
    val data: Data,
) : IImageBuffer {
    private val imageInfo = ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL)

    private var _image: Image? = null
    val image: Image get() {
        if (_image == null) {
            _image = Image.makeRaster(imageInfo, data, stride)
        }
        return _image!!
    }
}

actual object ImageBufferAdapter
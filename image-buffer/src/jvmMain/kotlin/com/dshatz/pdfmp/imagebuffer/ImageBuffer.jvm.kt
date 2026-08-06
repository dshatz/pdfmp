package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.wrapper.JvmJniAdapter
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.Managed

@JniAdapter(BitmapAdapter::class)
actual data class ImageBuffer(
    val pixmap: Pixmap
) : IImageBuffer {
    actual override val width: Int = pixmap.info.width
    actual override val height: Int = pixmap.info.height
    actual override val stride: Int = pixmap.rowBytes
}

object BitmapAdapter: JvmJniAdapter<ImageBuffer, SkBitmapInfo> {
    override fun getJniValue(value: ImageBuffer): SkBitmapInfo {
        return SkBitmapInfo(
            value.pixmap.addr,
            value.width,
            value.height,
            value.stride
        )
    }

    override fun fromJniValue(value: SkBitmapInfo): ImageBuffer {
        val data = Data.makeWithoutCopy(
            value.ptr,
            length = value.strideBytes * value.height,
            object: Managed(value.ptr, 0, false) {}
        )
        val pixmap = Pixmap.make(
            ImageInfo(
                ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, null),
                value.height,
                value.width
            ),
            data,
            value.strideBytes
        )
        return ImageBuffer(pixmap)
    }

}

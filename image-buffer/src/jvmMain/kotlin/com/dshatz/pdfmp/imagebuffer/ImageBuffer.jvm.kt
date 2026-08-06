package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.wrapper.JvmJniAdapter
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.Managed

@JniAdapter(ImageBufferAdapter::class)
actual data class ImageBuffer(
    val pixmap: Pixmap
) : IImageBuffer {
    actual override val width: Int = pixmap.info.width
    actual override val height: Int = pixmap.info.height
    actual override val stride: Int = pixmap.rowBytes
    actual override fun close() {
        pixmap.close()
    }

    actual override fun reset() {

    }
}

actual object ImageBufferAdapter: JvmJniAdapter<ImageBuffer, SkBitmapInfo> {
    override fun getJniValue(value: ImageBuffer): SkBitmapInfo {
        val addr = value.pixmap.buffer.writableData()
        return SkBitmapInfo(
            addr,
            value.width,
            value.height,
            value.stride
        )
    }

    override fun fromJniValue(value: SkBitmapInfo): ImageBuffer {
        val addr = value.ptr
        println("Received buffer address $addr from jvm")
        val data = Data.makeWithoutCopy(
            addr,
            length = value.strideBytes * value.height,
            object: Managed(value.ptr, 0, false) {}
        )
        val pixmap = Pixmap.make(
            ImageInfo(
                ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, null),
                value.width,
                value.height
            ),
            data,
            value.strideBytes
        )
        return ImageBuffer(pixmap)
    }

}

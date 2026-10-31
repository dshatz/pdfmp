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
actual class ImageBuffer(
    val pixmap: Pixmap
) : IImageBuffer {
    actual override val width: Int = pixmap.info.width
    actual override val height: Int = pixmap.info.height
    actual override val stride: Int = pixmap.rowBytes

    /** Set by [reset] on the render thread; the draw thread then replaces the image. */
    @Volatile
    private var imageStale = false
    private var _image: Image? = null

    /** Cached so the GPU texture is uploaded once per render, not once per frame. Only call from the draw thread. */
    val image: Image get() {
        if (imageStale) {
            imageStale = false
            _image?.close()
            _image = null
        }
        return _image ?: Image.makeFromPixmap(pixmap).also { _image = it }
    }

    actual override fun close() {
        _image?.close()
        _image = null
        // The Data owns the pixels; the pixmap only points at them.
        val data = pixmap.buffer
        pixmap.close()
        data.close()
    }

    actual override fun reset() {
        imageStale = true
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

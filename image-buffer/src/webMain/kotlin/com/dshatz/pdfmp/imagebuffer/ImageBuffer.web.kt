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

    // Identity semantics. The generated data class equality would compare `data`, and Skia's Data.equals is a
    // memcmp of the contents: two buffers with the same pixels (e.g. blank tiles) would be "equal", which makes
    // sets of buffers drop or remove the wrong one, and every comparison scans up to a whole page of pixels.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = identityHash

    private val identityHash = nextIdentityHash++

    private var _image: Image? = null
    val image: Image get() {
        if (_image == null) {
            _image = Image.makeRaster(imageInfo, data, stride)
        }
        return _image!!
    }

    private var closed = false

    actual override fun close() {
        if (closed) {
            // Closing twice would free the same Skia memory twice; it means the pool lost track of this buffer.
            println("WARN! ImageBuffer ${width}x$height closed twice")
            return
        }
        closed = true
        _image?.close()
        data.close()
        ImageBufferStats.onClose(stride * height)
    }

    actual override fun reset() {
        _image?.close()
        _image = null
    }
}

actual object ImageBufferAdapter

private var nextIdentityHash = 0

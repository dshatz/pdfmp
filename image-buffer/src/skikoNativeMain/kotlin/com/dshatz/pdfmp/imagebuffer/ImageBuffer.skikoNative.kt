package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toCPointer
import org.jetbrains.skia.Image
import org.jetbrains.skia.Pixmap
import kotlin.concurrent.Volatile

@JniAdapter(adapter = ImageBufferAdapter::class)
actual data class ImageBuffer(
    val pixmap: Pixmap
): IImageBuffer, WritableImageBuffer {
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
        // Called after every native render (see withFpdfBitmap), so it must not free anything; skiko frees on GC.
    }

    actual override fun reset() {
        imageStale = true
    }

    override fun <R> withWritableAddress(block: (address: CPointer<*>) -> R) {
        block(pixmap.buffer.writableData().toLong().toCPointer<ByteVar>()!!)
    }
}

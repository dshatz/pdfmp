package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.annotations.JniAdapter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toCPointer
import org.jetbrains.skia.Pixmap

@JniAdapter(adapter = ImageBufferAdapter::class)
actual data class ImageBuffer(
    val pixmap: Pixmap
): IImageBuffer, WritableImageBuffer {
    actual override val width: Int = pixmap.info.width
    actual override val height: Int = pixmap.info.height
    actual override val stride: Int = pixmap.rowBytes
    actual override fun close() {
        // jvm will close and skiko will call native.
    }

    actual override fun reset() {

    }

    override fun <R> withWritableAddress(block: (address: CPointer<*>) -> R) {
        block(pixmap.buffer.writableData().toLong().toCPointer<ByteVar>()!!)
    }
}

package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.SizeB
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.impl.NativePointer


expect fun NativePointer.toLong(): Long
actual class ConsumerBuffer(
    val skiaBitmap: Bitmap,
): IConsumerBuffer {

    actual override val dimensions: BufferDimensions = BufferDimensions(
        skiaBitmap.width,
        skiaBitmap.height,
        skiaBitmap.rowBytes
    )
    actual override fun <T> withAddress(action: (Long) -> T): T {
        return skiaBitmap.peekPixels()?.buffer?.writableData()?.toLong()?.let(action) ?: error("Could not read bitmap buffer address")
    }

    actual override fun capacity(): SizeB {
        return SizeB(skiaBitmap.computeByteSize().toLong())
    }

    private var free = true
    actual override fun free() {
        free = true
    }

    actual override val isFree: Boolean
        get() = free

    actual override fun setUnfree() {
        free = false
    }

    actual override fun dispose() {
        skiaBitmap.close()
    }
}

actual object ConsumerBufferUtil {
    actual fun allocate(size: SizeB, width: Int, height: Int): ConsumerBuffer {
        val imageInfo = ImageInfo(
            width,
            height,
            ColorType.BGRA_8888,
            ColorAlphaType.PREMUL
        )

        val skiaBitmap = Bitmap()
        skiaBitmap.allocPixels(imageInfo)
        return ConsumerBuffer(skiaBitmap)
    }
}
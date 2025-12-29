package com.dshatz.pdfmp

import android.graphics.Bitmap
import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.SizeB


actual class ConsumerBuffer(
    val androidBitmap: Bitmap,
    val bufferInfo: BufferInfo,
): IConsumerBuffer {

    actual override val dimensions: BufferDimensions = bufferInfo.dimensions

    actual override fun <T> withAddress(action: (Long) -> T): T {
        return action(bufferInfo.address)
    }

    actual override fun capacity(): SizeB {
        return SizeB(androidBitmap.byteCount.toLong())
    }


    actual override fun free() {
        _isFree = true
        // Do not unlock the bitmap here as this buffer will be reused by ConsumerBufferPool.
    }

    actual override fun dispose() {
        ConsumerBufferUtil.unlockBitmap(androidBitmap)
        androidBitmap.recycle()
    }

    private var _isFree = true
    actual override val isFree: Boolean get() = _isFree


    actual override fun setUnfree() {
        _isFree = false
    }
}

actual object ConsumerBufferUtil {
    private external fun lockBitmap(bitmap: Bitmap): BufferInfo
    external fun unlockBitmap(bitmap: Bitmap)
    actual fun allocate(size: SizeB, width: Int, height: Int): ConsumerBuffer {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val info = lockBitmap(bitmap)
        return ConsumerBuffer(
            bitmap,
            info
        )
    }
}
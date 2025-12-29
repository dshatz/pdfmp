package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.SizeB

expect class ConsumerBuffer: IConsumerBuffer {
    override fun <T> withAddress(action: (Long) -> T): T
    override fun capacity(): SizeB
    override fun free()
    override fun dispose()
    override val isFree: Boolean
    override fun setUnfree()
    override val dimensions: BufferDimensions
}

interface IConsumerBuffer {
    fun <T> withAddress(action: (Long) -> T): T
    fun capacity(): SizeB

    /**
     * Mark the buffer as not in use and ready to be reused.
     */
    fun free()

    /**
     * Completely throw the buffer in the trash. On Android, this should call Bitmap.recycle to free video memory.
     */
    fun dispose()
    val isFree: Boolean
    fun setUnfree()

    val dimensions: BufferDimensions
}

expect object ConsumerBufferUtil {
    fun allocate(size: SizeB, width: Int, height: Int): ConsumerBuffer
}
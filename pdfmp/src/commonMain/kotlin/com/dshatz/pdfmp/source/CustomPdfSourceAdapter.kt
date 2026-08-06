package com.dshatz.pdfmp.source

import com.dshatz.kni.annotations.JniCall
import com.dshatz.kni.annotations.JniCallback
import com.dshatz.kni.buffers.ByteBuffer

@JniCallback
interface CustomPdfSourceAdapter: AutoCloseable {

    suspend fun getDocumentLength(): Long

    /**
     * Implementations must write exactly [ByteBuffer.capacity] bytes to [buffer].
     *
     * @param position offset in the source file.
     *
     * @return how many bytes were written.
     */
    suspend fun readBlock(position: Long, buffer: ByteBuffer): Int
}

expect class GetLengthCallback(): AutoCloseable {
    @JniCall
    fun onLength(length: Long)
    override fun close()
}

expect class ReadBlockCallback(): AutoCloseable {
    @JniCall
    fun onBlock(bytes: Int)
    override fun close()
}
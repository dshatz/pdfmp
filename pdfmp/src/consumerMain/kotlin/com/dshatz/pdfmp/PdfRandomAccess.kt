package com.dshatz.pdfmp

import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter

/**
 * A document that can be read at any position, see [PdfData.randomAccess]. pdfmp keeps what it read and only asks for
 * what it still needs, merging neighbouring parts into one read, so implementations don't need a cache.
 * Reads may run concurrently.
 */
interface PdfRandomAccess : AutoCloseable {
    suspend fun length(): Long

    /** Reads exactly [length] bytes at [position] into [destination] from [offset]. */
    suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int)

    override fun close() {}
}

internal class RandomAccessAdapter(private val source: PdfRandomAccess) : CustomPdfSourceAdapter {
    override suspend fun getDocumentLength(): Long = source.length()

    override suspend fun readBlock(position: Long, buffer: ByteBuffer): Int {
        val size = buffer.capacity.toInt()
        val bytes = ByteArray(size)
        source.read(position, bytes, 0, size)
        buffer.write(bytes)
        return size
    }

    override fun close() = source.close()
}

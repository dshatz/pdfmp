package com.dshatz.pdfmp.source

import com.dshatz.kni.annotations.CallableFromNative
import com.dshatz.kni.buffers.ByteBuffer

@CallableFromNative
interface CustomPdfSourceAdapter: AutoCloseable {

    fun getDocumentLength(): Long

    /**
     * Implementations must write exactly [ByteBuffer.capacity] bytes to [buffer].
     *
     * @param position offset in the source file.
     *
     * @return how many bytes were written.
     */
    fun readBlock(position: Long, buffer: ByteBuffer): Int

    fun setError(error: String?)
    fun getLastError(): String?
}
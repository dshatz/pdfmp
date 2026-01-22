package com.dshatz.pdfmp.jni

import dev.datlag.nkommons.CallableFromNative
import dev.datlag.nkommons.binding.ByteBuffer

@CallableFromNative
interface CustomPdfSourceAdapter: AutoCloseable {

    suspend fun getDocumentLength(): Int

    /**
     * Implementations must write exactly [ByteBuffer.capacity] bytes to [buffer].
     *
     * @param position offset in the source file.
     *
     * @return how many bytes were written.
     */
    suspend fun readBlock(position: Long, buffer: ByteBuffer): Int
}
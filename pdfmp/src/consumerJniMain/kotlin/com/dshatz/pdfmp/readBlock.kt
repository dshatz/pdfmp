package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PlatformByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.nio.ByteBuffer

/*
fun readBlockSync(
    position: Long,
    customPdfSourceAdapter: CustomPdfSourceAdapter,
    buffer: ByteBuffer
): ByteArray {
    val result = runCatching {
        runBlocking(Dispatchers.IO) {
            customPdfSourceAdapter.readBlock(position, PlatformByteBuffer(buffer))
        }
    }
    val buffer = Buffer()
    result.pack(buffer, Buffer::writeInt)
    return buffer.readByteArray()
}

fun getDocumentLength(
    customPdfSourceAdapter: CustomPdfSourceAdapter
): ByteArray {
    val result = runCatching {
        runBlocking(Dispatchers.IO) {
            customPdfSourceAdapter.getDocumentLength()
        }
    }
    val buffer = Buffer()
    result.pack(buffer, Buffer::writeLong)
    return buffer.readByteArray()
}*/

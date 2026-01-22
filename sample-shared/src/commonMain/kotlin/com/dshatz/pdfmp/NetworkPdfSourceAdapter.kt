package com.dshatz.pdfmp

import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking

class NetworkPdfSourceAdapter(
    private val url: String,
    private val client: HttpClient
) : CustomPdfSourceAdapter {

    private val chunkSize = 64 * 1024 // 64KB
    private val chunkCache = mutableMapOf<Int, ByteArray>()

    private val length: Long by lazy {
        runBlocking(Dispatchers.IO) {
            val response = client.head(url) {
                expectSuccess = true
            }
            response.contentLength() ?: error("No content length")
        }
    }

    override fun getDocumentLength(): Long = length

    override fun readBlock(position: Long, buffer: ByteBuffer): Int = runBlocking {
        val size = buffer.capacity.toInt()
        val result = ByteArray(size)
        var bytesCopied = 0

        while (bytesCopied < size) {
            val currentPos = position + bytesCopied
            val chunkIndex = (currentPos / chunkSize).toInt()
            val chunkOffset = (currentPos % chunkSize).toInt()

            val chunkData = getOrFetchChunk(chunkIndex)

            val availableInChunk = chunkSize - chunkOffset
            val needed = size - bytesCopied
            val toCopy = minOf(availableInChunk, needed)

            chunkData.copyInto(
                destination = result,
                destinationOffset = bytesCopied,
                startIndex = chunkOffset,
                endIndex = chunkOffset + toCopy
            )
            bytesCopied += toCopy
        }
        buffer.put(result)
        bytesCopied
    }

    override fun setError(error: String?) {
        w("Error from c: $error")
    }

    override fun getLastError(): String? {
        return null
    }


    private suspend fun getOrFetchChunk(index: Int): ByteArray {
        return chunkCache.getOrPut(index) {
            val start = index.toLong() * chunkSize
            val end = minOf(start + chunkSize - 1, length - 1)

            val response = client.get(url) {
                header(HttpHeaders.Range, "bytes=$start-$end")
            }

            if (response.status != HttpStatusCode.PartialContent && response.status != HttpStatusCode.OK) {
                throw Exception("Range request failed")
            }

            val bytes = response.readBytes()
            if (bytes.size < chunkSize && (start + bytes.size) < length) {
                val fullChunk = ByteArray(chunkSize)
                bytes.copyInto(fullChunk)
                fullChunk
            } else {
                bytes
            }
        }
    }

    override fun close() {
        chunkCache.clear()
    }
}
package com.dshatz.pdfmp

import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.GetLengthCallback
import com.dshatz.pdfmp.source.ReadBlockCallback
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkPdfSourceAdapter(
    private val url: String,
    private val client: HttpClient
) : CustomPdfSourceAdapter {

    private val chunkSize = 64 * 1024 // 64KB
    private val chunkCache = mutableMapOf<Int, ByteArray>()

    private var documentLength: Long? = null
    private suspend fun fetchLength(): Long {
        val response = client.head(url) {
            expectSuccess = true
        }
//        documentLength = response.contentLength() ?: error("No content length")
        return (response.contentLength() ?: error("No content length")).also {
            documentLength = it
        }
    }

    override suspend fun getDocumentLength(): Long {
        return fetchLength()
        /*documentLength?.let(callback::onLength) ?: scope.launch {
            fetchLength()
            callback.onLength(documentLength!!)
        }*/
    }

    override suspend fun readBlock(position: Long, buffer: ByteBuffer): Int {
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
        buffer.write(result)
        return bytesCopied
    }


    private suspend fun getOrFetchChunk(index: Int): ByteArray {
        return chunkCache.getOrPut(index) {
            val start = index.toLong() * chunkSize
            val end = minOf(start + chunkSize - 1, documentLength!! - 1)

            val response = client.get(url) {
                header(HttpHeaders.Range, "bytes=$start-$end")
            }

            if (response.status != HttpStatusCode.PartialContent && response.status != HttpStatusCode.OK) {
                throw Exception("Range request failed")
            }

            val bytes = response.readBytes()
            if (bytes.size < chunkSize && (start + bytes.size) < documentLength!!) {
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
package com.dshatz.pdfmp

import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength

/**
 * Reads a PDF over HTTP with range requests. pdfmp keeps what it read and asks only for what is missing, merging
 * neighbouring chunks into one read, so every read is a single request with no caching here.
 */
class NetworkPdfSourceAdapter(
    private val url: String,
    private val client: HttpClient
) : CustomPdfSourceAdapter {

    override suspend fun getDocumentLength(): Long {
        val response = client.head(url) {
            expectSuccess = true
        }
        return response.contentLength() ?: error("No content length")
    }

    override suspend fun readBlock(position: Long, buffer: ByteBuffer): Int {
        val size = buffer.capacity.toInt()
        val response = client.get(url) {
            header(HttpHeaders.Range, "bytes=$position-${position + size - 1}")
        }
        // A 200 would be the whole file: the server ignored the range.
        check(response.status == HttpStatusCode.PartialContent) { "Range request failed: ${response.status}" }
        val bytes = response.readRawBytes()
        check(bytes.size == size) { "Expected $size bytes at $position, got ${bytes.size}" }
        buffer.write(bytes)
        return size
    }

    override fun close() {}
}

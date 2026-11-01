package com.dshatz.pdfmp

internal const val HTTP_OK = 200
internal const val HTTP_PARTIAL_CONTENT = 206

internal class HttpResponse(val status: Int, val body: ByteArray)

/** The platform's HTTP client, for [PdfData.url]. */
internal expect object PdfHttp {
    /** The document size from a HEAD request, or null if unknown. */
    suspend fun contentLength(url: String, headers: Map<String, String>): Long?

    /** GET, of the bytes in [range] if given. Doesn't throw on HTTP errors: check [HttpResponse.status]. */
    suspend fun get(url: String, headers: Map<String, String>, range: LongRange? = null): HttpResponse
}

internal class HttpRandomAccess(
    private val url: String,
    private val headers: Map<String, String>,
    private val length: Long,
) : PdfRandomAccess {
    override suspend fun length() = length

    override suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int) {
        val response = PdfHttp.get(url, headers, position..<position + length)
        check(response.status == HTTP_PARTIAL_CONTENT) { "Range request to $url failed: HTTP ${response.status}" }
        check(response.body.size == length) { "Expected $length bytes at $position, got ${response.body.size}" }
        response.body.copyInto(destination, offset)
    }
}

/** The Range header value for [range]. */
internal fun rangeHeader(range: LongRange) = "bytes=${range.first}-${range.last}"

package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import kotlinx.io.files.Path

/**
 * A PDF document to open. Create one with the factories below, or the platform ones such as `PdfData.fromStream` on
 * JVM and Android or `PdfData.fromBlob` on the web.
 *
 * Documents that are not entirely in memory or in a local file are read progressively: only the parts needed for the
 * pages being displayed are read.
 */
abstract class PdfData internal constructor() {
    // Implementations are data classes: an equal PdfData is the same document, so `rememberPdfState(PdfData.url(…))`
    // doesn't reopen it on every recomposition.

    /** Opens the document. [context] gives access to platform resources, e.g. Android assets and content URIs. */
    @InternalPdfmpApi
    abstract suspend fun createRenderer(context: PlatformContext): PdfRenderer

    companion object {
        /** A document entirely in memory. */
        fun bytes(bytes: ByteArray): PdfData = SourceData(PdfSource.PdfBytes(bytes))

        /** A local file, read in place. Not supported on the web. */
        fun file(path: Path): PdfData = SourceData(PdfSource.PdfPath(path))

        /**
         * A remote document, read with HTTP range requests. Falls back to downloading the whole document if the server
         * doesn't support them. On the web, the server must allow cross-origin requests (CORS).
         */
        fun url(url: String, headers: Map<String, String> = emptyMap()): PdfData = UrlData(url, headers)

        /**
         * A document at [uri], read in place where possible:
         * - `http:` and `https:` (see [url]);
         * - `file:`;
         * - Android: `content:` and `file:///android_asset/`;
         * - JVM: `jar:`;
         * - web: URLs relative to the page.
         *
         * Accepts the URIs of Compose Multiplatform resources: `PdfData.uri(Res.getUri("files/document.pdf"))`.
         */
        fun uri(uri: String): PdfData = UriData(uri)

        /** Any random-access source; only the parts needed are read. */
        fun randomAccess(source: PdfRandomAccess): PdfData = RandomAccessData(source)
    }
}

internal data class SourceData(private val source: PdfSource) : PdfData() {
    override suspend fun createRenderer(context: PlatformContext) = PdfRenderer(source)
}

internal data class RandomAccessData(private val source: PdfRandomAccess) : PdfData() {
    override suspend fun createRenderer(context: PlatformContext) = PdfRenderer(RandomAccessAdapter(source))
}

internal data class UrlData(private val url: String, private val headers: Map<String, String>) : PdfData() {
    override suspend fun createRenderer(context: PlatformContext): PdfRenderer {
        val length = PdfHttp.contentLength(url, headers)
        // A range request tells whether ranges work. If not, the server sent the whole document: use it.
        val probe = PdfHttp.get(url, headers, range = 0L..0L)
        if (length != null && probe.status == HTTP_PARTIAL_CONTENT) {
            return RandomAccessData(HttpRandomAccess(url, headers, length)).createRenderer(context)
        }
        check(probe.status == HTTP_OK) { "Could not download $url: HTTP ${probe.status}" }
        return bytes(probe.body).createRenderer(context)
    }
}

internal data class UriData(private val uri: String) : PdfData() {
    override suspend fun createRenderer(context: PlatformContext): PdfRenderer {
        val data = when (uri.substringBefore(':', missingDelimiterValue = "").lowercase()) {
            "http", "https" -> url(uri)
            else -> platformUriData(uri, context) ?: when {
                uri.startsWith("file:", ignoreCase = true) -> file(Path(fileUriPath(uri)))
                else -> error("Unsupported URI: $uri")
            }
        }
        return data.createRenderer(context)
    }
}

/** [PdfData] for the URI schemes specific to this platform, or null to handle [uri] as a common one. */
internal expect suspend fun platformUriData(uri: String, context: PlatformContext): PdfData?

/** The path of a `file:` URI: `file:/a`, `file:///a` and `file://localhost/a` are all `/a`. */
internal fun fileUriPath(uri: String): String {
    val withoutScheme = uri.substringAfter(':')
    val path = if (withoutScheme.startsWith("//")) "/" + withoutScheme.removePrefix("//").substringAfter('/') else withoutScheme
    return percentDecode(path)
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        if (value[i] == '%' && i + 2 < value.length) {
            bytes += value.substring(i + 1, i + 3).toInt(16).toByte()
            i += 3
        } else {
            bytes += value[i].toString().encodeToByteArray().toList()
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}

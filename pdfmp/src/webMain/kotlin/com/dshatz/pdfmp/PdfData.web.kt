@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.files.Blob
import kotlin.js.Promise

actual abstract class PlatformContext internal constructor()

@InternalPdfmpApi
actual val noPlatformContext: PlatformContext? = object : PlatformContext() {}

/** A local file, e.g. from `<input type="file">` or drag and drop. Only the parts needed are read. */
fun PdfData.Companion.fromBlob(blob: Blob): PdfData = randomAccess(BlobRandomAccess(blob))

private data class BlobRandomAccess(private val blob: Blob) : PdfRandomAccess {
    override suspend fun length() = blobSize(blob).toLong()

    override suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int) {
        val bytes = Int8Array(blobSlice(blob, position.toDouble(), (position + length).toDouble()).await()).toByteArray()
        bytes.copyInto(destination, offset)
    }
}

/** URLs relative to the page, e.g. the URIs of Compose resources. */
internal actual suspend fun platformUriData(uri: String, context: PlatformContext): PdfData? =
    if (SCHEME.containsMatchIn(uri)) null else PdfData.url(resolveUrl(uri))

private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

internal actual object PdfHttp {
    private suspend fun fetch(url: String, method: String, headers: Map<String, String>, range: LongRange?): JsAny {
        val jsHeaders = newHeaders()
        headers.forEach { (name, value) -> appendHeader(jsHeaders, name, value) }
        if (range != null) appendHeader(jsHeaders, "Range", rangeHeader(range))
        return fetch(url, method, jsHeaders).await()
    }

    actual suspend fun contentLength(url: String, headers: Map<String, String>): Long? {
        val response = fetch(url, "HEAD", headers, range = null)
        if (status(response) != HTTP_OK) return null
        return contentLengthHeader(response)?.toLongOrNull()
    }

    actual suspend fun get(url: String, headers: Map<String, String>, range: LongRange?): HttpResponse {
        val response = fetch(url, "GET", headers, range)
        return HttpResponse(status(response), Int8Array(arrayBuffer(response).await()).toByteArray())
    }
}

@JsFun("(blob) => blob.size")
private external fun blobSize(blob: Blob): Double

@JsFun("(blob, start, end) => blob.slice(start, end).arrayBuffer()")
private external fun blobSlice(blob: Blob, start: Double, end: Double): Promise<ArrayBuffer>

@JsFun("(url) => new URL(url, globalThis.document ? document.baseURI : self.location.href).href")
private external fun resolveUrl(url: String): String

@JsFun("() => new Headers()")
private external fun newHeaders(): JsAny

@JsFun("(headers, name, value) => headers.append(name, value)")
private external fun appendHeader(headers: JsAny, name: String, value: String)

@JsFun("(url, method, headers) => fetch(url, { method, headers })")
private external fun fetch(url: String, method: String, headers: JsAny): Promise<JsAny>

@JsFun("(response) => response.status")
private external fun status(response: JsAny): Int

@JsFun("(response) => response.headers.get('Content-Length')")
private external fun contentLengthHeader(response: JsAny): String?

@JsFun("(response) => response.arrayBuffer()")
private external fun arrayBuffer(response: JsAny): Promise<ArrayBuffer>

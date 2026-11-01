package com.dshatz.pdfmp

import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

actual abstract class PlatformContext internal constructor()

@InternalPdfmpApi
actual val noPlatformContext: PlatformContext? = object : PlatformContext() {}

/** `jar:` (e.g. Compose resources): native code can't read inside the jar, so it reads a temporary copy. */
internal actual suspend fun platformUriData(uri: String, context: PlatformContext): PdfData? =
    if (uri.startsWith("jar:", ignoreCase = true)) temporaryCopy(URI(uri).toURL().openStream()) else null

internal actual object PdfHttp {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    }

    private fun request(url: String, headers: Map<String, String>) =
        HttpRequest.newBuilder(URI(url)).apply { headers.forEach { (name, value) -> header(name, value) } }

    actual suspend fun contentLength(url: String, headers: Map<String, String>): Long? {
        val head = request(url, headers).method("HEAD", HttpRequest.BodyPublishers.noBody()).build()
        val response = client.sendAsync(head, BodyHandlers.discarding()).await()
        if (response.statusCode() != HTTP_OK) return null
        return response.headers().firstValueAsLong("Content-Length").takeIf { it.isPresent }?.asLong
    }

    actual suspend fun get(url: String, headers: Map<String, String>, range: LongRange?): HttpResponse {
        val get = request(url, headers).apply { if (range != null) header("Range", rangeHeader(range)) }.GET().build()
        val response = client.sendAsync(get, BodyHandlers.ofByteArray()).await()
        return HttpResponse(response.statusCode(), response.body())
    }
}

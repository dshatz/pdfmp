@file:OptIn(ExperimentalForeignApi::class)

package com.dshatz.pdfmp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual abstract class PlatformContext internal constructor()

@InternalPdfmpApi
actual val noPlatformContext: PlatformContext? = object : PlatformContext() {}

internal actual suspend fun platformUriData(uri: String, context: PlatformContext): PdfData? = null

internal actual object PdfHttp {
    actual suspend fun contentLength(url: String, headers: Map<String, String>): Long? {
        val (response, _) = send(url, headers, "HEAD", range = null)
        if (response.statusCode.toInt() != HTTP_OK) return null
        return response.expectedContentLength.takeIf { it >= 0 }
    }

    actual suspend fun get(url: String, headers: Map<String, String>, range: LongRange?): HttpResponse {
        val (response, data) = send(url, headers, "GET", range)
        return HttpResponse(response.statusCode.toInt(), data?.toByteArray() ?: ByteArray(0))
    }

    private suspend fun send(
        url: String,
        headers: Map<String, String>,
        method: String,
        range: LongRange?,
    ): Pair<NSHTTPURLResponse, NSData?> = suspendCancellableCoroutine { continuation ->
        val request = NSMutableURLRequest(NSURL(string = url))
        request.setHTTPMethod(method)
        headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
        if (range != null) request.setValue(rangeHeader(range), forHTTPHeaderField = "Range")
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                continuation.resumeWithException(IllegalStateException("Request to $url failed: ${error.localizedDescription}"))
            } else {
                continuation.resume((response as NSHTTPURLResponse) to data)
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    if (isNotEmpty()) usePinned { memcpy(it.addressOf(0), bytes, length) }
}

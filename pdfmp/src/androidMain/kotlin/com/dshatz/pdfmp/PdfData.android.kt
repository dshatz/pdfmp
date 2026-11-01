package com.dshatz.pdfmp

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

actual typealias PlatformContext = Context

@InternalPdfmpApi
actual val noPlatformContext: PlatformContext? = null

/**
 * `content:` (e.g. from the system file picker) and `file:///android_asset/` (e.g. Compose resources), read in place
 * through their file descriptor when they have one, otherwise from a temporary copy.
 */
internal actual suspend fun platformUriData(uri: String, context: PlatformContext): PdfData? = when {
    uri.startsWith("content:", ignoreCase = true) -> withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")
            ?: error("Could not open $uri")
        descriptor.randomAccessOrNull() ?: temporaryCopy(descriptor.createInputStream())
    }
    uri.startsWith(ANDROID_ASSET, ignoreCase = true) -> withContext(Dispatchers.IO) {
        val path = fileUriPath(uri).removePrefix("/android_asset/")
        // Compressed assets have no file descriptor.
        val descriptor = try { context.assets.openFd(path) } catch (_: FileNotFoundException) { null }
        descriptor?.randomAccessOrNull() ?: temporaryCopy(context.assets.open(path))
    }
    else -> null
}

private const val ANDROID_ASSET = "file:///android_asset/"

/** Null if the length is unknown, e.g. for a pipe. */
private fun AssetFileDescriptor.randomAccessOrNull(): PdfData? {
    if (length == AssetFileDescriptor.UNKNOWN_LENGTH) return null
    val stream = createInputStream()
    return PdfData.randomAccess(FileChannelRandomAccess(stream.channel, startOffset, length, this))
}

internal actual object PdfHttp {
    private fun open(url: String, headers: Map<String, String>, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

    actual suspend fun contentLength(url: String, headers: Map<String, String>): Long? = withContext(Dispatchers.IO) {
        val connection = open(url, headers, "HEAD")
        try {
            if (connection.responseCode != HTTP_OK) null else connection.contentLengthLong.takeIf { it >= 0 }
        } finally {
            connection.disconnect()
        }
    }

    actual suspend fun get(url: String, headers: Map<String, String>, range: LongRange?): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = open(url, headers, "GET")
            if (range != null) connection.setRequestProperty("Range", rangeHeader(range))
            try {
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                HttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
            } finally {
                connection.disconnect()
            }
        }
}

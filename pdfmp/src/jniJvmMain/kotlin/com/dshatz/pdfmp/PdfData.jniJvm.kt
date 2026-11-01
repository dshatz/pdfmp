package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A document read from [stream], which this takes ownership of. A [FileInputStream] is read in place; any other
 * stream is copied to a temporary file first, as PDFs need random access.
 */
fun PdfData.Companion.fromStream(stream: InputStream): PdfData = StreamData(stream)

internal data class StreamData(private val stream: InputStream) : PdfData() {
    override suspend fun createRenderer(context: PlatformContext): PdfRenderer {
        val data = if (stream is FileInputStream) {
            val channel = stream.channel
            val start = channel.position()
            randomAccess(FileChannelRandomAccess(channel, start, channel.size() - start, stream))
        } else {
            temporaryCopy(stream)
        }
        return data.createRenderer(context)
    }
}

/** [length] bytes of [channel] from [start], e.g. an uncompressed Android asset inside the APK. */
internal class FileChannelRandomAccess(
    private val channel: FileChannel,
    private val start: Long,
    private val length: Long,
    private val owner: Closeable,
) : PdfRandomAccess {
    override suspend fun length() = length

    override suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int) =
        withContext(Dispatchers.IO) {
            val buffer = ByteBuffer.wrap(destination, offset, length)
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, start + position + buffer.position() - offset)
                check(read >= 0) { "Unexpected end of document at ${position + buffer.position() - offset}" }
            }
        }

    override fun close() = owner.close()
}

/** Copies [stream] to a temporary file, read in place and deleted when the document closes. */
internal suspend fun temporaryCopy(stream: InputStream): PdfData = withContext(Dispatchers.IO) {
    val file = File.createTempFile("pdfmp", ".pdf")
    try {
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
    } catch (e: Throwable) {
        file.delete()
        throw e
    }
    SourceData(PdfSource.PdfPath(Path(file.absolutePath), deleteOnClose = true))
}

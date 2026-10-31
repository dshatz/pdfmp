package com.dshatz.pdfmp.pdfium

import kotlinx.serialization.Serializable

/** Result of an FPDFAvail check: when [status] is [NOT_AVAILABLE], the chunks to fetch before checking again. */
@Serializable
data class Availability(val status: Int, val missingChunks: List<Int>) {
    companion object {
        const val ERROR = -1
        const val NOT_AVAILABLE = 0
        const val AVAILABLE = 1
    }
}

/**
 * A document loaded with pdfium's FPDFAvail API from data that arrives in chunks. pdfium only reads chunks that
 * arrived; the availability checks report what is missing, which the caller fetches before checking again. pdfium
 * therefore never waits for a read, which a web worker couldn't do and which would block pdfium's thread elsewhere.
 *
 * The whole file is reserved in memory up front and filled in as chunks arrive.
 */
expect class ProgressiveDocument(length: Int, chunkSize: Int) {
    val chunks: ProgressiveChunks

    fun isDocAvail(): Availability
    fun isPageAvail(pageIndex: Int): Availability

    /** The document, once [isDocAvail] reported it available. */
    fun document(): DocHandle?

    /** Call after closing the [document]. */
    fun destroy()
}

/** Which chunks of a [ProgressiveDocument] arrived, and which pdfium asked for during the current check. */
class ProgressiveChunks(val length: Int, val chunkSize: Int) {
    private val received = BooleanArray((length + chunkSize - 1) / chunkSize)
    private val missing = mutableSetOf<Int>()

    fun isAvailable(offset: Long, size: Long): Boolean = size <= 0 || chunksOf(offset, size).all { received[it] }

    /** Marks the chunks from [offset] for [size] bytes as arrived. Only the last chunk of the file may be partial. */
    fun markReceived(offset: Int, size: Int) {
        require(offset + size <= length) { "Chunk data beyond the end of the document" }
        chunksOf(offset.toLong(), size.toLong()).forEach { received[it] = true }
    }

    /** Records that pdfium needs [size] bytes at [offset], unless they arrived. Returns whether they arrived. */
    fun request(offset: Long, size: Long): Boolean {
        if (isAvailable(offset, size)) return true
        missing.addAll(chunksOf(offset, size))
        return false
    }

    /** Runs an FPDFAvail check, returning its status with the chunks pdfium asked for meanwhile. */
    inline fun check(block: () -> Int): Availability {
        startCheck()
        try {
            val status = block()
            return Availability(status, if (status == Availability.NOT_AVAILABLE) missingChunks() else emptyList())
        } finally {
            endCheck()
        }
    }

    @PublishedApi
    internal fun startCheck() {
        missing.clear()
        ProgressiveCallbacks.checking = this
    }

    @PublishedApi
    internal fun missingChunks(): List<Int> = missing.sorted()

    @PublishedApi
    internal fun endCheck() {
        ProgressiveCallbacks.checking = null
        missing.clear()
    }

    private fun chunksOf(offset: Long, size: Long): IntRange =
        (offset / chunkSize).toInt()..(minOf(offset + size, length.toLong()) - 1).toInt() / chunkSize
}

/**
 * Logic of the pdfium callbacks, shared by every [ProgressiveDocument]. The callbacks only receive a pointer back, so
 * documents are registered under it; the platforms convert pointers to [Long] keys. Download hints come without any
 * pointer to the document, but checks run one at a time, so they go to the document being checked.
 */
object ProgressiveCallbacks {
    private val documents = HashMap<Long, ProgressiveChunks>()

    @PublishedApi
    internal var checking: ProgressiveChunks? = null

    fun register(key: Long, chunks: ProgressiveChunks) {
        documents[key] = chunks
    }

    fun unregister(key: Long) {
        documents.remove(key)
    }

    /** FX_FILEAVAIL.IsDataAvail. */
    fun isDataAvail(key: Long, offset: Long, size: Long): Boolean = documents.getValue(key).request(offset, size)

    /** FPDF_FILEACCESS.m_GetBlock: [copy] runs only if the data arrived. */
    inline fun getBlock(key: Long, position: Long, size: Long, copy: () -> Unit): Boolean {
        if (!isDataAvail(key, position, size)) return false
        copy()
        return true
    }

    /** FX_DOWNLOADHINTS.AddSegment. */
    fun addSegment(offset: Long, size: Long) {
        checking?.request(offset, size)
    }
}

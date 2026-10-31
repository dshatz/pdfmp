package com.dshatz.pdfmp

import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.imagebuffer.ImageBufferUtil
import com.dshatz.pdfmp.imagebuffer.SizeB
import com.dshatz.pdfmp.imagebuffer.bytes
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException

class ImageBufferPool {

    data class PageBuffer(
        val buffer: ImageBuffer,
        val dimensions: PageDimensions
    )

    internal val pageBuffers: LinkedHashMap<Int, PageBuffer> = linkedMapOf()
    internal val freePageBuffers: LinkedHashSet<ImageBuffer> = linkedSetOf()

    /** Keyed by the whole tile, scale included: after a zoom, the old scale's tile may still be displayed. */
    internal val tilePool: HashMap<PdfTile, ImageBuffer> = hashMapOf()
    internal val freeTileBuffers: LinkedHashSet<ImageBuffer> = linkedSetOf()

    private val lock = ReentrantLock()

    /** Buffers native code is writing into. Never handed out or closed until the render ends, even if freed. */
    private val rendering: HashSet<ImageBuffer> = hashSetOf()

    private val closeAfterRender: HashSet<ImageBuffer> = hashSetOf()

    /**
     * Set by [close]. Renders still running for the closed document must not allocate into this pool:
     * nothing would ever close those buffers.
     */
    private var closed = false

    private fun checkNotClosed() {
        // Cancellation, not an error: the render is simply no longer needed.
        if (closed) throw CancellationException("ImageBufferPool is closed")
    }

    suspend inline fun <T> withRendering(buffer: ImageBuffer, block: () -> T): T {
        beginRender(buffer)
        try {
            return block()
        } finally {
            endRender(buffer)
        }
    }

    @PublishedApi
    internal fun beginRender(buffer: ImageBuffer) = lock.withLock {
        rendering += buffer
    }

    @PublishedApi
    internal fun endRender(buffer: ImageBuffer) = lock.withLock {
        rendering -= buffer
        if (closeAfterRender.remove(buffer)) buffer.close()
    }

    private fun closeWhenIdle(buffer: ImageBuffer) {
        if (buffer in rendering) closeAfterRender += buffer else buffer.close()
    }

    fun getBufferPage(page: Int, dimensions: PageDimensions): Pair<ImageBuffer, Boolean> = lock.withLock {
        checkNotClosed()
        val existing = pageBuffers[page]
        if (existing != null) {
            if (existing.dimensions == dimensions) {
                return existing.buffer to false
            } else {
                pageBuffers[page] = PageBuffer(existing.buffer, dimensions)
                return existing.buffer to true
            }
        } else {
            val reuse = freePageBuffers.firstOrNull {
                it.width == dimensions.width && it.height == dimensions.height && it !in rendering
            }
            if (reuse != null) {
                freePageBuffers.remove(reuse)
                pageBuffers[page] = PageBuffer(reuse, dimensions)
                return reuse to true
            } else {
                // No free buffer has the right size (e.g. after a resize), so none of them will be reused.
                // Close them before allocating so their memory can be reused instead of growing the heap
                // (wasm heaps never shrink).
                freePageBuffers.forEach { closeWhenIdle(it) }
                freePageBuffers.clear()
                val newBuffer = ImageBufferUtil.allocate(dimensions.width, dimensions.height)
                pageBuffers[page] = PageBuffer(newBuffer, dimensions)
                printBufferSpace()
                return newBuffer to true
            }
        }
    }

    fun freePageBuffer(page: Int) = lock.withLock {
        val freed = pageBuffers.remove(page)
        freed?.let { freePageBuffers.add(it.buffer) }
        freePageBuffers.trimTo(MAX_FREE_PAGE_BUFFERS)
        printBufferSpace()
    }

    fun getBufferTile(tile: PdfTile): Pair<ImageBuffer, Boolean> = lock.withLock {
        checkNotClosed()
        tilePool[tile]?.let { return it to false }
        val free = freeTileBuffers.firstOrNull { it !in rendering }
        free?.let { freeTileBuffers.remove(it) }
        val freshBuffer = free ?: ImageBufferUtil.allocate(PdfTile.WIDTH, PdfTile.HEIGHT)
        tilePool[tile] = freshBuffer
        printBufferSpace()
        return freshBuffer to true
    }

    fun tileBuffer(tile: PdfTile): ImageBuffer? = lock.withLock { tilePool[tile] }

    fun freeTileBuffer(tile: PdfTile) = lock.withLock {
        tilePool.remove(tile)?.let { freeTileBuffers += it }
        freeTileBuffers.trimTo(MAX_FREE_TILE_BUFFERS)
        printBufferSpace()
    }

    private fun printBufferSpace() {
        if (!shouldLog(LogLevel.DEBUG)) return
        val usedSize = tilePool.values.sumOf { it.height * it.stride }.bytes
        val freeSize = freeTileBuffers.sumOf { it.height * it.stride }.bytes
        d("Tile buffers: used = ${usedSize.stringMB}; free = ${freeSize.stringMB}")

        val usedPageSize = pageBuffers.values.sumOf { it.buffer.height * it.buffer.stride }.bytes
        val freePageSize = freePageBuffers.sumOf { it.height * it.stride }.bytes
        d("Page buffers: used = ${usedPageSize.stringMB}; free = ${freePageSize.stringMB}")
    }

    /** Closes the oldest buffers until at most [max] remain. */
    private fun LinkedHashSet<ImageBuffer>.trimTo(max: Int) {
        while (size > max) {
            val oldest = first()
            remove(oldest)
            closeWhenIdle(oldest)
        }
    }

    fun close() = lock.withLock {
        closed = true
        tilePool.values.forEach { closeWhenIdle(it) }
        pageBuffers.values.forEach { closeWhenIdle(it.buffer) }
        freeTileBuffers.forEach { closeWhenIdle(it) }
        freePageBuffers.forEach { closeWhenIdle(it) }
        tilePool.clear()
        pageBuffers.clear()
        freeTileBuffers.clear()
        freePageBuffers.clear()
    }

    companion object {
        // Enough to reuse a buffer when a page or a row of tiles scrolls out while another scrolls in.
        private const val MAX_FREE_PAGE_BUFFERS = 2
        private const val MAX_FREE_TILE_BUFFERS = 32 // 256x256 tiles: 256 KB each, 8 MB total.
    }
}
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

    internal val tilePool: HashMap<TileKey, TileBuffer> = hashMapOf()
    internal val freeTileBuffers: LinkedHashSet<ImageBuffer> = linkedSetOf()

    data class TileBuffer(
        val buffer: ImageBuffer,
        val renderedPageDimensions: PageDimensions
    )

    private val pageBufferLock = ReentrantLock()

    /**
     * Set by [close]. Renders still running for the closed document must not allocate into this pool:
     * nothing would ever close those buffers.
     */
    private var closed = false

    private fun checkNotClosed() {
        // Cancellation, not an error: the render is simply no longer needed.
        if (closed) throw CancellationException("ImageBufferPool is closed")
    }

    fun getBufferPage(page: Int, dimensions: PageDimensions): Pair<ImageBuffer, Boolean> = pageBufferLock.withLock {
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
            val reuse = freePageBuffers.firstOrNull { it.width == dimensions.width && it.height == dimensions.height }
            if (reuse != null) {
                freePageBuffers.remove(reuse)
                pageBuffers[page] = PageBuffer(reuse, dimensions)
                return reuse to true
            } else {
                // No free buffer has the right size (e.g. after a resize), so none of them will be reused.
                // Close them before allocating so their memory can be reused instead of growing the heap
                // (wasm heaps never shrink).
                freePageBuffers.forEach { it.close() }
                freePageBuffers.clear()
                val newBuffer = ImageBufferUtil.allocate(dimensions.width, dimensions.height)
                pageBuffers[page] = PageBuffer(newBuffer, dimensions)
                printBufferSpace()
                return newBuffer to true
            }
        }
    }

    fun freePageBuffer(page: Int) = pageBufferLock.withLock {
        val freed = pageBuffers.remove(page)
        freed?.let { freePageBuffers.add(it.buffer) }
        freePageBuffers.trimTo(MAX_FREE_PAGE_BUFFERS)
        printBufferSpace()
    }

    private val tileBufferLock = ReentrantLock()

    fun getBufferTile(tile: PdfTile): Pair<ImageBuffer, Boolean> = tileBufferLock.withLock {
        checkNotClosed()
        val (w, h) = PdfTile.WIDTH to PdfTile.HEIGHT
        val existing = tilePool[tile.key]
        if (existing != null) {
            if (existing.renderedPageDimensions == tile.scaledPage) {
                // Existing tile buffer was already filled by this tile at the same scale.
                return existing.buffer to false
            }
            else {
                // Existing tile buffer was filled by this tile but at different scale.
                // So we need to repaint
                tilePool[tile.key] = TileBuffer(existing.buffer, tile.scaledPage)
                return existing.buffer to true
            }
        }
        else {
            val free = freeTileBuffers.firstOrNull()
            free?.let { freeTileBuffers.remove(it) }
            val freshBuffer = free ?: ImageBufferUtil.allocate(w, h)
            tilePool[tile.key] = TileBuffer(freshBuffer, tile.scaledPage)
            printBufferSpace()
            return freshBuffer to true
        }
    }

    fun freeTileBuffer(tile: PdfTile) = tileBufferLock.withLock {
        val used = tilePool[tile.key]
        used?.let { tileBuffer ->
            tilePool.remove(tile.key)
            freeTileBuffers += tileBuffer.buffer
        }
        freeTileBuffers.trimTo(MAX_FREE_TILE_BUFFERS)
        printBufferSpace()
    }

    private fun printBufferSpace() {
        if (!shouldLog(LogLevel.DEBUG)) return
        val usedSize = tilePool.values.sumOf { it.buffer.height * it.buffer.stride }.bytes
        val freeSize = freeTileBuffers.sumOf { it.height * it.stride }.bytes
        d("Tile buffers: used = ${usedSize.stringMB}; free = ${freeSize.stringMB}")

        val usedPageSize = pageBuffers.values.sumOf { it.buffer.height * it.buffer.stride }.bytes
        val freePageSize = freePageBuffers.sumOf { it.height * it.stride }.bytes
        d("Page buffers: used = ${usedPageSize.stringMB}; free = ${freePageSize.stringMB}")
    }

    /** Closes the oldest buffers until at most [max] remain. Free buffers are not displayed, so closing them is safe. */
    private fun LinkedHashSet<ImageBuffer>.trimTo(max: Int) {
        while (size > max) {
            val oldest = first()
            remove(oldest)
            oldest.close()
        }
    }

    fun close() = pageBufferLock.withLock {
        tileBufferLock.withLock {
            closed = true
            tilePool.values.forEach {
                it.buffer.close()
            }
            pageBuffers.values.forEach {
                it.buffer.close()
            }
            freeTileBuffers.forEach { it.close() }
            freePageBuffers.forEach { it.close() }
            tilePool.clear()
            pageBuffers.clear()
            freeTileBuffers.clear()
            freePageBuffers.clear()
        }
    }

    companion object {
        // Enough to reuse a buffer when a page or a row of tiles scrolls out while another scrolls in.
        private const val MAX_FREE_PAGE_BUFFERS = 2
        private const val MAX_FREE_TILE_BUFFERS = 32 // 256x256 tiles: 256 KB each, 8 MB total.
    }
}
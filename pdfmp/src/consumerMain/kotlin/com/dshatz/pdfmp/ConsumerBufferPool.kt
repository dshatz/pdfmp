package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.SizeB
import com.dshatz.pdfmp.model.bytes
import com.dshatz.pdfmp.model.calculateSize
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConsumerBufferPool {

    internal val buffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()
    internal var bufferViewport: ConsumerBuffer? = null

    internal val tilePool: HashMap<TileKey, TileBuffer> = hashMapOf()
    internal val freeTileBuffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()

    data class TileBuffer(
        val buffer: ConsumerBuffer,
        val renderedPageDimensions: PageDimensions
    )

    fun getBufferPage(transform: PageTransform): ConsumerBuffer {
        val neededCapacity = transform.bufferSize
        val sliceSize = transform.sliceSize()
        val page = transform.pageIndex
        val reuse = buffers.firstOrNull { it.isFree && it.capacity() >= neededCapacity }
        if (reuse == null) {
//            d("Not reusing page buffer. required dimensions: $sliceSize")
        }
        val buffer = reuse ?: run {
            val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, sliceSize.first, sliceSize.second)
            buffers.add(newBuffer)
//            d("[$page] Allocated ${neededCapacity.stringMB} ${transform.scaledWidth} x ${transform.scaledHeight}")
//            d("Total buffer memory: ${totalBufferMemory.stringMB}, Unfree: ${totalUnfreeBufferMemory.stringMB}")
            newBuffer
        }
        buffer.setUnfree()

        return buffer
    }

    fun getBufferViewport(transforms: List<PageTransform>): ConsumerBuffer {
        val (w, h) = transforms.calculateSize()
        val neededCapacity = SizeB(w * h * 4L)

        val reuse = bufferViewport?.takeIf {
            w <= it.dimensions.width && h <= it.dimensions.height && it.capacity() >= neededCapacity
        }
        if (reuse == null) {
//            d("Not reusing viewport buffer. Existing: ${bufferViewport?.dimensions}, required: $w x $h")
        }
        return reuse ?: run {
            // Free old viewport buffer memory. We are allocating a new one because the viewport got bigger.
            bufferViewport?.dispose()
            val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, w, h)
//            d("Allocated viewport buffer ${neededCapacity.stringMB}")
            this.bufferViewport = newBuffer
            newBuffer
        }
    }

    private val tileBufferLock = ReentrantLock()

    fun getBufferTile(tile: PdfTile): Pair<ConsumerBuffer, Boolean> = tileBufferLock.withLock {
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
            val freshBuffer = free ?: ConsumerBufferUtil.allocate((w * h * 4L).bytes, w, h).also {
//                printBufferSpace()
            }
            freshBuffer.setUnfree()
            tilePool[tile.key] = TileBuffer(freshBuffer, tile.scaledPage)
            return freshBuffer to true
        }
    }

    fun freeTileBuffer(tile: PdfTile) = tileBufferLock.withLock {
        val used = tilePool[tile.key]
        used?.let { tileBuffer ->
            tilePool.remove(tile.key)
            freeTileBuffers += tileBuffer.buffer
            tileBuffer.buffer.free()
        }
    }

    private fun printBufferSpace() {
        val usedSize = tilePool.values.sumOf { it.buffer.capacity().bytes }
        val freeSize = freeTileBuffers.sumOf { it.capacity().bytes }
        d("Tile buffers: used = ${SizeB(usedSize).stringMB}; free = ${SizeB(freeSize).stringMB}")
    }

    internal val totalBufferMemory: SizeB
        get() = buffers.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }

    internal val totalUnfreeBufferMemory: SizeB
        get() = buffers.filter { !it.isFree }.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }
}
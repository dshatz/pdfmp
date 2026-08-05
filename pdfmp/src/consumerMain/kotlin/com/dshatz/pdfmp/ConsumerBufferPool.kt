package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.SizeB
import com.dshatz.pdfmp.model.bytes
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConsumerBufferPool {

    internal val pageBuffers: LinkedHashMap<Int, ConsumerBuffer> = linkedMapOf()
    internal val freePageBuffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()

    internal val tilePool: HashMap<TileKey, TileBuffer> = hashMapOf()
    internal val freeTileBuffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()

    data class TileBuffer(
        val buffer: ConsumerBuffer,
        val renderedPageDimensions: PageDimensions
    )

    private val pageBufferLock = ReentrantLock()

    fun getBufferPage(page: Int, dimensions: PageDimensions): ConsumerBuffer = pageBufferLock.withLock {
        val neededCapacity = (dimensions.width * dimensions.height * 4L).bytes
        val existing = pageBuffers[page]
        if (existing != null) {
            return existing
        } else {
            val reuse = freePageBuffers.firstOrNull()
            if (reuse != null) {
                freePageBuffers.remove(reuse)
                pageBuffers[page] = reuse
                return reuse
            } else {
                val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, dimensions.width, dimensions.height)
                pageBuffers[page] = newBuffer
                return newBuffer
            }
        }
    }

    fun freePageBuffer(page: Int) = pageBufferLock.withLock {
        val freed = pageBuffers.remove(page)
        freed?.let(freePageBuffers::add)
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
}
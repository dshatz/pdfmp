package com.dshatz.pdfmp

import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.imagebuffer.ImageBufferUtil
import com.dshatz.pdfmp.imagebuffer.SizeB
import com.dshatz.pdfmp.imagebuffer.bytes
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

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

    fun getBufferPage(page: Int, dimensions: PageDimensions): Pair<ImageBuffer, Boolean> = pageBufferLock.withLock {
        val neededCapacity = (dimensions.width * dimensions.height * 4L).bytes
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
                val newBuffer = ImageBufferUtil.allocate(dimensions.width, dimensions.height)
                pageBuffers[page] = PageBuffer(newBuffer, dimensions)
                return newBuffer to true
            }
        }
    }

    fun freePageBuffer(page: Int) = pageBufferLock.withLock {
        val freed = pageBuffers.remove(page)
        freed?.let { freePageBuffers.add(it.buffer) }
    }

    private val tileBufferLock = ReentrantLock()

    fun getBufferTile(tile: PdfTile): Pair<ImageBuffer, Boolean> = tileBufferLock.withLock {
        val (w, h) = PdfTile.WIDTH to PdfTile.HEIGHT
        val existing = tilePool[tile.key]
        if (existing != null) {
            if (existing.renderedPageDimensions == tile.scaledPage) {
                // Existing tile buffer was already filled by this tile at the same scale.
                w("Not repainting $tile")
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
            val freshBuffer = free ?: ImageBufferUtil.allocate(w, h).also {
//                printBufferSpace()
            }
            tilePool[tile.key] = TileBuffer(freshBuffer, tile.scaledPage)
            return freshBuffer to true
        }
    }

    fun freeTileBuffer(tile: PdfTile) = tileBufferLock.withLock {
        val used = tilePool[tile.key]
        used?.let { tileBuffer ->
            tilePool.remove(tile.key)
            freeTileBuffers += tileBuffer.buffer
        }
    }

    private fun printBufferSpace() {
        val usedSize = tilePool.values.sumOf { (it.buffer.width * it.buffer.stride) }.bytes
        val freeSize = freeTileBuffers.sumOf { it.width * it.stride }.bytes
        d("Tile buffers: used = ${usedSize.stringMB}; free = ${freeSize.stringMB}")
    }
}
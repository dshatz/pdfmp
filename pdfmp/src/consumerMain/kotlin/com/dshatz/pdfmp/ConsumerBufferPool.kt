package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.SizeB
import com.dshatz.pdfmp.model.calculateSize

class ConsumerBufferPool {

    internal val buffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()
    internal var bufferViewport: ConsumerBuffer? = null

    fun getBufferPage(transform: PageTransform): ConsumerBuffer {
        val neededCapacity = transform.bufferSize
        val sliceSize = transform.sliceSize()
        val page = transform.pageIndex
        val reuse = buffers.firstOrNull { it.isFree && it.capacity() >= neededCapacity }
        if (reuse == null) {
            d("Not reusing page buffer. required dimensions: $sliceSize")
        }
        val buffer = reuse ?: run {
            val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, sliceSize.first, sliceSize.second)
            buffers.add(newBuffer)
            d("[$page] Allocated ${neededCapacity.stringMB} ${transform.scaledWidth} x ${transform.scaledHeight}")
            d("Total buffer memory: ${totalBufferMemory.stringMB}, Unfree: ${totalUnfreeBufferMemory.stringMB}")
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
            d("Not reusing viewport buffer. Existing: ${bufferViewport?.dimensions}, required: $w x $h")
        }
        return reuse ?: run {
            // Free old viewport buffer memory. We are allocating a new one because the viewport got bigger.
            bufferViewport?.dispose()
            val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, w, h)
            d("Allocated viewport buffer ${neededCapacity.stringMB}")
            this.bufferViewport = newBuffer
            newBuffer
        }
    }

    internal val totalBufferMemory: SizeB
        get() = buffers.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }

    internal val totalUnfreeBufferMemory: SizeB
        get() = buffers.filter { !it.isFree }.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }
}
package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.SizeB
import com.dshatz.pdfmp.model.calculateSize

class ConsumerBufferPool {

    internal val buffers: LinkedHashSet<ConsumerBuffer> = linkedSetOf()
    internal var bufferViewport: ConsumerBuffer? = null

    /**
     * Viewport buffers replaced while still in use. A previous render coroutine may sit between
     * getBufferViewport() and withAddress(), and the currently displayed image references the
     * buffer's pixels zero-copy (extractSubset) — disposing inline frees memory under both.
     * Retired buffers are disposed on a later request, once their consumer has freed them.
     */
    internal val retiredViewportBuffers = mutableListOf<ConsumerBuffer>()

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
        // Dispose retired buffers whose consumers have released them since the last request.
        retiredViewportBuffers.removeAll { retired ->
            if (retired.isFree) {
                retired.dispose()
                true
            } else false
        }

        val (w, h) = transforms.calculateSize()
        val neededCapacity = SizeB(w * h * 4L)

        val reuse = bufferViewport?.takeIf {
            w <= it.dimensions.width && h <= it.dimensions.height && it.capacity() >= neededCapacity
        }
        if (reuse == null) {
            d("Not reusing viewport buffer. Existing: ${bufferViewport?.dimensions}, required: $w x $h")
        }
        val buffer = reuse ?: run {
            // The viewport got bigger. Never dispose the previous buffer inline — an in-flight
            // render or the currently displayed image may still hold it. Retire it; it is
            // disposed above on a later request once its consumer has freed it.
            bufferViewport?.let { old ->
                if (old.isFree) old.dispose() else retiredViewportBuffers.add(old)
            }
            val newBuffer = ConsumerBufferUtil.allocate(neededCapacity, w, h)
            d("Allocated viewport buffer ${neededCapacity.stringMB}")
            this.bufferViewport = newBuffer
            newBuffer
        }
        buffer.setUnfree()
        return buffer
    }

    internal val totalBufferMemory: SizeB
        get() = buffers.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }

    internal val totalUnfreeBufferMemory: SizeB
        get() = buffers.filter { !it.isFree }.fold(SizeB.ZERO) { s, buffer -> s + buffer.capacity() }
}
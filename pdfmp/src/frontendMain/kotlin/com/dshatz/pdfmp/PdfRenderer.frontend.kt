package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN

actual class PdfRenderer actual constructor(private val source: PdfSource) {

//    private var _buffer: ByteBuffer = ByteBuffer.allocateDirect(0).order(LITTLE_ENDIAN)

    val scaledBuffers = mutableMapOf<Int, ByteBuffer>()
    val baseBuffers = mutableMapOf<Int, ByteBuffer>()


    private fun allocateBuffer(renderRequest: RenderRequest): ByteBuffer {
        val neededCapacity = renderRequest.transform.viewportWidth * renderRequest.transform.viewportHeight * 4
        val bufferMap = if (renderRequest.transform.scale == 1f) {
            baseBuffers
        } else {
            scaledBuffers
        }
        val buffer = bufferMap[renderRequest.page]
        if (buffer == null || buffer.capacity() < neededCapacity) {
            println("Allocating direct buffer with capacity: $neededCapacity")
            bufferMap[renderRequest.page] = ByteBuffer.allocateDirect(neededCapacity).order(LITTLE_ENDIAN)
        }
        return bufferMap[renderRequest.page]!!
    }

    private val renderer = PDFBridge.openFile(source.pack())
    
    actual fun render(renderRequest: RenderRequest): RenderResponse {
        val buffer = allocateBuffer(renderRequest)
        val bufferAddress = PDFBridge.getBufferAddress(buffer)
        val packed = renderRequest.copy(bufferAddress = bufferAddress).pack()
        val response = RenderResponse.Companion.fromPacked(
            PDFBridge.render(renderer,packed)
        )
        return JvmRenderResponse(
            response.transform,
            response.pageSize,
            buffer
        )
    }

    actual fun close()  {
        PDFBridge.close(renderer)
    }

    actual fun getPageCount(): Int {
        return PDFBridge.getPageCount(renderer)
    }

    actual fun getAspectRatio(pageIndex: Int): Float {
        return PDFBridge.getAspectRatio(renderer, pageIndex)
    }

    actual fun getPageRatios(): List<Float> {
        return _root_ide_package_.com.dshatz.pdfmp.unpackFloats(PDFBridge.getPageRatios(renderer))
    }


    data class JvmRenderResponse(
        override val transform: ImageTransform,
        override val pageSize: PageSize,
        val byteBuffer: ByteBuffer
    ): RenderResponse(transform, pageSize)

}
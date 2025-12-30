package com.dshatz.pdfmp.model

import com.dshatz.pdfmp.model.BufferInfo.Companion.pack
import com.dshatz.pdfmp.packList
import com.dshatz.pdfmp.unpackList
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

data class RenderRequest(
    val transforms: List<PageTransform>,
    val pageSpacing: Int,
    val topOffset: Int,
    val bufferInfo: BufferInfo,
) {
    fun pack(): ByteArray {
        val buffer = Buffer()
        transforms.packList(
            buffer,
            packItem = PageTransform::pack
        )
        buffer.writeInt(pageSpacing)
        buffer.writeInt(topOffset)
        bufferInfo.pack(buffer)

        return buffer.readByteArray()
    }

    companion object {
        fun unpack(data: ByteArray): RenderRequest {
            val buffer = Buffer()
            buffer.write(data)
            val imageTransforms = unpackList(
                buffer,
                unpackItem = PageTransform::unpack
            )

            val pageSpacing = buffer.readInt()
            val topOffset = buffer.readInt()
            val bufferInfo = BufferInfo.unpack(buffer)

            return RenderRequest(
                imageTransforms,
                pageSpacing,
                topOffset,
                bufferInfo
            )
        }
    }
}

data class BufferDimensions(
    val width: Int,
    val height: Int,
    val stride: Int
) {
    fun withAddress(address: Long): BufferInfo {
        return BufferInfo(this, address)
    }
}

data class BufferInfo(
    val dimensions: BufferDimensions,
    val address: Long,
) {
    companion object {
        fun BufferInfo.pack(buffer: Buffer) {
            buffer.writeInt(dimensions.width)
            buffer.writeInt(dimensions.height)
            buffer.writeInt(dimensions.stride)
            buffer.writeLong(address)
        }

        fun unpack(buffer: Buffer): BufferInfo {
            return BufferInfo(
                BufferDimensions(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt()
                ),
                buffer.readLong()
            )
        }
    }
}
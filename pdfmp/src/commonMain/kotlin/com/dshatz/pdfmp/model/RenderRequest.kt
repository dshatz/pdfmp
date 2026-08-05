package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import com.dshatz.pdfmp.PageDimensions
import kotlinx.io.Buffer

@JniSerializable
data class RenderRequest(
    val page: Int,
    val dimensions: PageDimensions,
    val bufferInfo: BufferInfo,
)

@JniSerializable
data class BufferDimensions(
    val width: Int,
    val height: Int,
    val stride: Int
) {
    fun withAddress(address: Long): BufferInfo {
        return BufferInfo(this, address)
    }
}

@JniSerializable
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
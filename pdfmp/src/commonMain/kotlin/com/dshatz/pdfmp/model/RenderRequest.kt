package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import com.dshatz.pdfmp.model.BufferInfo.Companion.pack
import com.dshatz.pdfmp.packList
import com.dshatz.pdfmp.unpackList
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

@JniSerializable
data class RenderRequest(
    val transforms: List<PageTransform>,
    val pageSpacing: Int,
    val topOffset: Int,
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
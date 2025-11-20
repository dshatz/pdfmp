package com.dshatz.pdfmp

import kotlinx.io.*

data class ImageTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewportWidth: Int,
    val viewportHeight: Int
) {
    fun pack(): ByteArray {
        return Buffer().also {
            it.writeFloat(scale)
            it.writeFloat(offsetX)
            it.writeFloat(offsetY)
            it.writeInt(viewportWidth)
            it.writeInt(viewportHeight)
        }.readByteArray()
    }

    companion object {
        internal const val packedSizeBytes = Float.SIZE_BYTES * 3 + Int.SIZE_BYTES * 2
        fun unpack(data: ByteArray): ImageTransform {
            val buffer = Buffer()
            buffer.write(data, 0, packedSizeBytes)
            return ImageTransform(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readInt(),
                buffer.readInt()
            )
        }
    }
}

data class RenderRequest(
    val page: Int,
    val transform: ImageTransform,
    val bufferAddress: Long
) {
    fun pack(): ByteArray {
        val buffer = Buffer()

//        val pathString = path.toString()
//        val pathBytes = pathString.encodeToByteArray()
//        buffer.writeInt(pathBytes.size)
//        buffer.write(pathBytes)

        buffer.writeLong(bufferAddress)
        buffer.writeInt(page)
        buffer.write(transform.pack())

        return buffer.readByteArray()
    }

    companion object {
        fun unpack(data: ByteArray): RenderRequest {
            val buffer = Buffer()
            buffer.write(data)
//            val pathLen = buffer.readInt()
//            val pathString = buffer.readString(byteCount = pathLen.toLong())
//            val path = Path(pathString)
            val bufferAddr = buffer.readLong()
            val page = buffer.readInt()
            return RenderRequest(
                page, ImageTransform.unpack(buffer.readByteArray()), bufferAddr
            )
        }
    }
}


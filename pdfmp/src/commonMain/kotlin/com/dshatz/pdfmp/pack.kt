package com.dshatz.pdfmp

import kotlinx.io.*

internal fun <T> List<T>.packList(to: Buffer, packItem: T.(Buffer) -> Unit) {
    to.writeInt(size)
    forEach {
        it.packItem(to)
    }
}

internal fun <T> unpackList(buffer: Buffer, unpackItem: Buffer.() -> T): List<T> {
    val size = buffer.readInt()
    return buildList {
        repeat(size) {
            add(buffer.unpackItem())
        }
    }
}

private fun Buffer.writeLengthString(
    string: String
) {
    writeInt(string.length)
    writeString(string)
}

private fun Buffer.readLengthString(): String {
    val length = readInt()
    return readString(length.toLong())
}

internal fun <T> Result<T>.pack(
    buffer: Buffer,
    packData: Buffer.(T) -> Unit
) {
    this.map {
        buffer.writeByte(1)
        packData(buffer, it)
    }.getOrElse {
        buffer.writeByte(0)
        buffer.writeLengthString(it.message.orEmpty())
        buffer.writeLengthString(it.stackTraceToString())
    }
}

internal fun <T> unpackResultOrThrow(
    bytes: ByteArray,
    unpackData: Buffer.() -> T
): T {
    return unpackResult(bytes, unpackData).getOrThrow()
}

@PublishedApi
internal fun <T> unpackResult(
    bytes: ByteArray,
    unpackData: Buffer.() -> T
): Result<T> {
    val buffer = Buffer()
    buffer.write(bytes)
    val success = buffer.readByte() == 1.toByte()
    if (success) {
        return Result.success(unpackData(buffer))
    } else {
        val message = buffer.readLengthString()
        val stackTrace = buffer.readLengthString()
        return Result.failure(PDFFMPNativeException(
            message,
            stackTrace
        ))
    }
}

class PDFFMPNativeException(
    message: String,
    val nativeStackTrace: String
): RuntimeException(message)
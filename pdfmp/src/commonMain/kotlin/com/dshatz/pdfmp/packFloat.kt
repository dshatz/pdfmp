package com.dshatz.pdfmp

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readFloat
import kotlinx.io.writeFloat

fun List<Float>.pack(): ByteArray {
    val buffer = Buffer()
    forEach(buffer::writeFloat)
    return buffer.readByteArray()
}

fun unpackFloats(packed: ByteArray): List<Float> {
    val buffer = Buffer()
    buffer.write(packed)
    val result = mutableListOf<Float>()
    while (!buffer.exhausted()) {
        result += buffer.readFloat()
    }
    return result
}
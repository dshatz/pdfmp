package com.dshatz.pdfmp.source

import kotlinx.io.files.Path

sealed interface PdfSource {
    val marker: Byte
    fun packValue(): ByteArray
    fun pack(): ByteArray {
        return byteArrayOf(marker) + packValue()
    }
    companion object {
        fun unpack(packed: ByteArray): PdfSource {
            return when (val marker = packed.first()) {
                0x11.toByte() -> PdfPath(Path(
                    packed.sliceArray(1..<packed.size).decodeToString()
                ))
                0x12.toByte() -> PdfBytes(
                    packed.sliceArray(1..<packed.size)
                )
                else -> error("Could not unpack pdfsource. Marker = $marker")
            }
        }
    }

    data class PdfPath(val path: Path): PdfSource {
        override val marker: Byte = 0x11

        override fun packValue(): ByteArray {
            return path.toString().encodeToByteArray()
        }
    }

    data class PdfBytes(val bytes: ByteArray): PdfSource {
        override fun equals(other: Any?): Boolean {
            return other is PdfBytes && other.bytes.contentEquals(bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }

        override val marker: Byte = 0x12

        override fun packValue(): ByteArray {
            return bytes
        }
    }
}
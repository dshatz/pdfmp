package com.dshatz.pdfmp.source

import com.dshatz.kni.annotations.JniSerializable
import com.dshatz.kni.annotations.JniSerializerFor
import com.dshatz.kni.serialization.JniSerializer
import com.dshatz.kni.serialization.readLenString
import com.dshatz.kni.serialization.writeLenString
import com.dshatz.pdfmp.model.SizeB
import kotlinx.io.Buffer
import kotlinx.io.files.Path

@JniSerializerFor(Path::class)
object PathSerializer: JniSerializer<Path>("kotlinx.io.files.Path") {
    override fun packToBuffer(value: Path, buffer: Buffer) {
        buffer.writeLenString(value.toString())
    }

    override fun unpackFromBuffer(buffer: Buffer): Path {
        return Path(buffer.readLenString())
    }
}

@JniSerializable
sealed interface PdfSource {

    fun dispose()

    data class PdfPath(@JniSerializable(with = PathSerializer::class) val path: Path): PdfSource {
        override fun dispose() {}
    }

    data class PdfBytes(val bytes: ByteArray): PdfSource {
        override fun equals(other: Any?): Boolean {
            return other is PdfBytes && other.bytes.contentEquals(bytes)
        }

        override fun hashCode(): Int {
            return bytes.contentHashCode()
        }

        override fun dispose() {}
        override fun toString(): String {
            return "PdfBytes(<${SizeB(bytes.size.toLong())}>)"
        }
    }
    /*data class Custom(val customSourceDescriptor: CustomSourceDescriptor): PdfSource {
        override fun dispose() {
            customSourceDescriptor.dispose()
        }
    }*/
}
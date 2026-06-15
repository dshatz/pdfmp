package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import kotlinx.io.Buffer
import kotlinx.io.readFloat
import kotlinx.io.writeFloat
import kotlin.math.max

@JniSerializable
data class PageTransform(
    val pageIndex: Int,
    val topCutoff: Int,
    val bottomCutoff: Int,
    val leftCutoff: Int,
    val rightCutoff: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
    val topGap: Int,
    val scale: Float,
) {

    fun sliceSize(): Pair<Int, Int> {
        return scaledWidth - leftCutoff - rightCutoff to scaledHeight - topCutoff - bottomCutoff
    }

    val bufferSize: SizeB get() {
        val (width, height) = sliceSize()
        return SizeB(width * height * 4L)
    }

    fun uncut(): PageTransform {
        return copy(
            topCutoff = 0,
            bottomCutoff = 0,
            leftCutoff = 0,
            rightCutoff = 0
        )
    }

    fun pack(to: Buffer) {
        to.let {
            it.writeInt(pageIndex)
            it.writeInt(topCutoff)
            it.writeInt(bottomCutoff)
            it.writeInt(leftCutoff)
            it.writeInt(rightCutoff)
            it.writeInt(scaledWidth)
            it.writeInt(scaledHeight)
            it.writeInt(topGap)
            it.writeFloat(scale)
        }
    }

    companion object {
        fun unpack(buffer: Buffer): PageTransform {
            return PageTransform(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readFloat()
            )
        }
    }
}

fun List<PageTransform>.calculateSize(): Pair<Int, Int> {
    val width = this.fold(0) { width, new ->
        max(width, new.sliceSize().first)
    }
    val height = this.fold(0) { height, new ->
        height + new.sliceSize().second + new.topGap
    }
    return width to height
}
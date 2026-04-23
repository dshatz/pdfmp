package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import kotlin.jvm.JvmInline

@JvmInline
@JniSerializable
value class SizeB(val bytes: Long): Comparable<SizeB> {
    val stringMB: String get() {
        if (bytes < 1024*1024) return "${bytes / 1024} kB"
        return "${bytes / 1024 / 1024} MB"
    }

    operator fun plus(other: SizeB) = SizeB(bytes + other.bytes)
    override fun compareTo(other: SizeB): Int = bytes.compareTo(other.bytes)

    override fun toString(): String = stringMB

    companion object {
        val ZERO = SizeB(0)
    }
}

val Long.bytes get() = SizeB(this)
val Int.bytes get() = SizeB(this.toLong())
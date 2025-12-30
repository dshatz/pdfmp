package com.dshatz.pdfmp.model

import kotlin.jvm.JvmInline

@JvmInline
value class SizeB(val bytes: Long): Comparable<SizeB> {
    val stringMB: String get() {
        return "${bytes / 1024 / 1024} MB"
    }

    operator fun plus(other: SizeB) = SizeB(bytes + other.bytes)
    override fun compareTo(other: SizeB): Int = bytes.compareTo(other.bytes)

    companion object {
        val ZERO = SizeB(0)
    }
}

val Long.bytes get() = SizeB(this)
val Int.bytes get() = SizeB(this.toLong())
package com.dshatz.pdfmp

value class Ptr(
    val ptr: UInt
) {
    fun notZeroOrNull(): Ptr? {
        return if (ptr == 0u) null else this
    }
}

fun UInt.wasmPointer(): Ptr? {
    return takeUnless { it == 0u }?.let(::Ptr)
}
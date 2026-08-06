package com.dshatz.pdfmp.pdfium

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.toCPointer

actual class DataPointer(
    val ptr: CPointer<ByteVarOf<Byte>>,
) {
    constructor(pinnedData: Pinned<ByteArray>): this(pinnedData.addressOf(0))

    actual companion object {
        actual fun fromLongPointer(ptr: Long): DataPointer {
            return DataPointer(ptr.toCPointer<ByteVarOf<Byte>>()!!)
        }
    }
}


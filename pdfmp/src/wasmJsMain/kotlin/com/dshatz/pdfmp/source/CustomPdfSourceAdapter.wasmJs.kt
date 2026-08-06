package com.dshatz.pdfmp.source

import com.dshatz.kni.annotations.JniCall

actual class GetLengthCallback actual constructor() : AutoCloseable {
    @JniCall
    actual fun onLength(length: Long) {
    }

    actual override fun close() {
    }
}

actual class ReadBlockCallback actual constructor() : AutoCloseable {
    @JniCall
    actual fun onBlock(bytes: Int) {
    }

    actual override fun close() {
    }
}
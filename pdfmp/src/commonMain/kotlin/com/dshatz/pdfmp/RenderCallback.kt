package com.dshatz.pdfmp

import com.dshatz.kni.annotations.CallableFromNative

@CallableFromNative
interface RenderCallback: AutoCloseable {
    fun onSuccess(result: ByteArray)
    fun onFailure(message: String)
}
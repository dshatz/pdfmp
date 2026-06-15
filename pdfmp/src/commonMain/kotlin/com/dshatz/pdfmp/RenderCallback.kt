package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCallback
import com.dshatz.pdfmp.model.RenderResponse

@JniCallback
interface RenderCallback: AutoCloseable {
    fun onSuccess(result: RenderResponse)
    fun onFailure(message: String)
}
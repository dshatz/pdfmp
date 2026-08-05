package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCallback

@JniCallback
interface RenderCallback: AutoCloseable {
    fun onSuccess()
    fun onFailure(message: String)
}

@JniCallback
interface TileRenderCallback: AutoCloseable {
    fun onSuccess()
    fun onFailure(message: String)
}

@JniCallback
interface PdfOperationCallback: AutoCloseable {
    fun onPageCount(count: Result<Int>)
    fun onPageRatio(ratio: Result<Float>)
}
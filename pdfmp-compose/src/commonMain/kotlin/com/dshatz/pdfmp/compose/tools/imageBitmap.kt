package com.dshatz.pdfmp.compose.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.compose.CurrentImage


internal expect fun ConsumerBuffer.toImageBitmap(): ImageBitmap

@Composable
internal expect fun CurrentImage.toImageBitmap(): RecyclableBitmap

data class RecyclableBitmap(
    val imageBitmap: ImageBitmap,
    val colorFilter: ColorFilter? = null,
    private val onTouch: () -> Unit = {}
) {
    /**
     * Let the GPU know that the actual pixels have changed.
     *
     * If we just write bytes to the backing buffer, gpu will not re-load those bytes and display stale pixels.
     */
    fun touch() {
        onTouch()
    }
}
package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.colorspace.ColorSpace
import com.dshatz.pdfmp.ConsumerBuffer

internal actual fun ConsumerBuffer.toImageBitmap(): ImageBitmap {
    return androidBitmap.asImageBitmap()
}

internal actual val bufferColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 1f, 0f, 0f,  // Red comes from Blue channel
            0f, 1f, 0f, 0f, 0f,  // Green stays Green
            1f, 0f, 0f, 0f, 0f,  // Blue comes from Red channel
            0f, 0f, 0f, 1f, 0f   // Alpha remains unchanged
        )
    )
)
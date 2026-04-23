package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.dshatz.pdfmp.ConsumerBuffer
import org.jetbrains.skia.ColorMatrix

internal actual fun ConsumerBuffer.toImageBitmap(): ImageBitmap {
    return skiaBitmap.asComposeImageBitmap()
}

internal actual val bufferColorFilter: ColorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix())
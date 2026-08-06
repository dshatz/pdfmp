package com.dshatz.pdfmp.compose.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Pixmap

internal actual fun ImageBuffer.toImageBitmap(): ImageBitmap {
    return skiaBitmap.asComposeImageBitmap()
}

internal actual val bufferColorFilter: ColorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix())

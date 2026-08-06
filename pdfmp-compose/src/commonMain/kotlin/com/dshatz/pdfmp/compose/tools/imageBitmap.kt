package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.unit.IntSize
import com.dshatz.pdfmp.imagebuffer.ImageBuffer

expect fun DrawContext.drawPixmap(
    buffer: ImageBuffer,
    dstOffset: Offset = Offset.Zero,
    dstSize: IntSize = IntSize(buffer.width, buffer.height)
)

internal expect val bufferColorFilter: ColorFilter


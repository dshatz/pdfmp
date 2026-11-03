package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

internal actual val bufferColorFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix())
actual fun DrawContext.drawPixmap(
    buffer: ImageBuffer,
    dstOffset: Offset,
    dstSize: IntSize
) {
    val image = Image.makeFromPixmap(buffer.pixmap)
    canvas.nativeCanvas.drawImageRect(
        image = image,
        dst = Rect(dstOffset.x, dstOffset.y, dstOffset.x + dstSize.width, dstOffset.y + dstSize.height),
        paint = null
    )
}
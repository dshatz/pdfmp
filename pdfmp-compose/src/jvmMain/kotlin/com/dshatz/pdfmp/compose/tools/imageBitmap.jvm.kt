package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint

actual fun DrawContext.drawPixmap(
    buffer: ImageBuffer,
    dstOffset: Offset,
    dstSize: IntSize,
    colorFilter: ColorFilter?
) {
    val paint = Paint().also {
        it.colorFilter = colorFilter?.asSkiaColorFilter()
    }
    val img = Image.makeFromPixmap(buffer.pixmap)
    canvas.nativeCanvas.drawImageRect(
        img,
        dst = org.jetbrains.skia.Rect(dstOffset.x, dstOffset.y, dstOffset.x + dstSize.width, dstOffset.y + dstSize.height),
        paint
    )
}

internal actual val bufferColorFilter: ColorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix())
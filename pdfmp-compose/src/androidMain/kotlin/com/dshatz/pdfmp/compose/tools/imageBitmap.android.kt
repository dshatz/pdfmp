package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.dshatz.pdfmp.imagebuffer.ImageBuffer

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

actual fun DrawContext.drawPixmap(
    buffer: ImageBuffer,
    dstOffset: Offset,
    dstSize: IntSize,
    colorFilter: ColorFilter?
) {
    val paint = android.graphics.Paint().also {
        it.colorFilter = colorFilter?.asAndroidColorFilter()
    }
    canvas.nativeCanvas.drawBitmap(
        buffer.bitmap,
        null,
        android.graphics.RectF(dstOffset.x, dstOffset.y, dstOffset.x + dstSize.width, dstOffset.y + dstSize.height),
        paint
    )
}
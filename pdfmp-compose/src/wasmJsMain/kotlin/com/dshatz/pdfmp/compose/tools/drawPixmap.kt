package com.dshatz.pdfmp.compose.tools
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect

actual fun DrawContext.drawPixmap(
    buffer: ImageBuffer,
    dstOffset: Offset,
    dstSize: IntSize,
    colorFilter: ColorFilter?
) {
    canvas.skiaCanvas.drawImageRect(
        image = buffer.image,
        dst = Rect(dstOffset.x, dstOffset.y, dstOffset.x + dstSize.width, dstOffset.y + dstSize.height),
        paint = null
    )
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

private val wasmPaint = Paint().also {
    it.colorFilter = bufferColorFilter.asSkiaColorFilter()
}.asComposePaint()

package com.dshatz.pdfmp.compose.tools

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import kotlin.math.min

@Composable
fun PartialBitmapRenderer(
    bitmap: ImageBitmap,
    srcOffset: IntOffset,
    scale: Float,
    dstSize: Size,
    modifier: Modifier = Modifier,
) {
    val imageBitmapSize = IntSize(bitmap.width, bitmap.height)

    Canvas(modifier = modifier) {

        val srcWidth = (size.width / scale).toInt()
        val srcHeight = (size.height / scale).toInt()


        // Ensure the source area doesn't exceed the bitmap bounds
        val finalSrcSize = IntSize(
            width = min(srcWidth, imageBitmapSize.width - srcOffset.x),
            height = min(srcHeight, imageBitmapSize.height - srcOffset.y)
        )


        // Draw the cropped/scaled image
        drawImage(
            image = bitmap,
            srcOffset = srcOffset,
            srcSize = finalSrcSize,
            dstOffset = IntOffset.Zero,
            dstSize = dstSize.toIntSize()
        )
    }
}
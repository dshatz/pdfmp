package com.dshatz.pdfmp.compose.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.CurrentImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skiko.toImage
import java.awt.image.BandedSampleModel
import java.awt.image.BufferedImage
import java.awt.image.ComponentSampleModel
import java.awt.image.DataBuffer
import java.awt.image.DataBufferByte
import java.awt.image.PixelInterleavedSampleModel
import java.awt.image.Raster
import java.awt.image.SampleModel

@Composable
internal actual fun CurrentImage.toImageBitmap(): RecyclableBitmap {
    val imageBitmap = remember(loadedTransform) {
        val buffer = (renderResponse as PdfRenderer.JvmRenderResponse).byteBuffer
        buffer.rewind()
        if (loadedTransform.viewportWidth == 0 || loadedTransform.viewportHeight == 0) {
            // Return an empty bitmap if dimensions are invalid
            RecyclableBitmap(ImageBitmap(1, 1), {})
        } else {
            val width = loadedTransform.viewportWidth
            val height = loadedTransform.viewportHeight
            // 1. Create a Java AWT BufferedImage
            val bufferedImage = BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_BGR
            )

            // Unfortunately we have to do a heap copy
            // since BufferedImage does not support reading from ByteBuffer.
            // Possibly we can call some native method to populate the pixels.
            val pixelInts = IntArray(width * height)
            buffer.asIntBuffer().get(pixelInts)

            bufferedImage.setRGB(
                0,
                0,
                loadedTransform.viewportWidth,
                loadedTransform.viewportHeight,
                pixelInts,
                0,
                loadedTransform.viewportWidth
            )

            ImageBitmap(width, height)
            RecyclableBitmap(bufferedImage.toComposeImageBitmap(), {

            })
        }
    }
    return imageBitmap
}
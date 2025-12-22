package com.dshatz.pdfmp.compose.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.dshatz.pdfmp.compose.CurrentImage
import com.dshatz.pdfmp.model.calculateSize
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

@Composable
internal actual fun CurrentImage.toImageBitmap(): RecyclableBitmap {
    val imageBitmap = remember(this.loadedTransforms, buffer) {
        val (width, height) = loadedTransforms.calculateSize()

        if (width == 0 || height == 0) {
            RecyclableBitmap(ImageBitmap(1, 1))
        } else {
            val bytes = buffer.byteArray

            val imageInfo = ImageInfo(
                width,
                height,
                ColorType.RGBA_8888,
                ColorAlphaType.PREMUL
            )

            val image = Image.makeRaster(
                imageInfo,
                bytes,
                width * 4
            )

            RecyclableBitmap(image.toComposeImageBitmap())
        }
    }
    return imageBitmap
}
package com.dshatz.pdfmp.compose.tools

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dshatz.pdfmp.compose.CurrentImage
import com.dshatz.pdfmp.model.calculateSize


private val BgrToRgbMatrix = ColorMatrix(
    floatArrayOf(
        0f, 0f, 1f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        1f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

@SuppressLint("UseKtx")
@Composable
internal actual fun CurrentImage.toImageBitmap(): RecyclableBitmap {
    val imageBitmap = remember(this.loadedTransforms, buffer) {
        val (width, height) = loadedTransforms.calculateSize()
        val bitmap = buffer.androidBitmap

        if (width == 0 || height == 0) {
            RecyclableBitmap(ImageBitmap(1, 1))
        } else {
            RecyclableBitmap(
                bitmap.asImageBitmap(),
                onTouch = {
                    bitmap.setPixel(0,0, bitmap.getPixel(0,0))
                },
                colorFilter = ColorMatrixColorFilter(BgrToRgbMatrix)
            )
        }
    }
    return imageBitmap
}

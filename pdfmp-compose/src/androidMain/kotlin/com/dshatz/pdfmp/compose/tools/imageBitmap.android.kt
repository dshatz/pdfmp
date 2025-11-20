package com.dshatz.pdfmp.compose.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.compose.CurrentImage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream

@Composable
internal actual fun CurrentImage.toImageBitmap(): RecyclableBitmap {
    val imageBitmap = remember(this.loadedTransform) {
        val width = loadedTransform.viewportWidth
        val height = loadedTransform.viewportHeight

        val buffer = (renderResponse as PdfRenderer.JvmRenderResponse).byteBuffer
        buffer.rewind()
        if (width == 0 || height == 0) {
            RecyclableBitmap(ImageBitmap(1, 1), {})
        } else {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            RecyclableBitmap(bitmap.asImageBitmap(), { bitmap.recycle() })
        }
    }
    return imageBitmap
}
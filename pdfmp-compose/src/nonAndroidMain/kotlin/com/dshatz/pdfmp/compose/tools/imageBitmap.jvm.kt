package com.dshatz.pdfmp.compose.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.dshatz.pdfmp.compose.CurrentImage
import com.dshatz.pdfmp.model.calculateSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect

@Composable
internal actual fun CurrentImage.toImageBitmap(): RecyclableBitmap {
    val imageBitmap = remember(loadedTransforms, buffer) {
        val subsetBitmap = Bitmap()
        val (w, h) = loadedTransforms.calculateSize()

        // When scrolling, sometimes the content height can change by 1 px even if viewport stays constant.
        // We dont create a new buffer for this in ConsumerBufferPool so here we have to remove any extra pixel rows, if any.
        buffer.skiaBitmap.extractSubset(
            subsetBitmap,
            IRect.makeWH(w, h)
        )

        RecyclableBitmap(subsetBitmap.asComposeImageBitmap())
    }
    return imageBitmap
}
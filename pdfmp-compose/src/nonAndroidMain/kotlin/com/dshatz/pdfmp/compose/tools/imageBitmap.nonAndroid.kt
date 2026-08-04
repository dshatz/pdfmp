package com.dshatz.pdfmp.compose.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.model.calculateSize
import com.dshatz.pdfmp.w
import jdk.internal.org.jline.utils.Colors.h
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.IRect

internal actual fun ConsumerBuffer.toImageBitmap(): ImageBitmap {
    return skiaBitmap.asComposeImageBitmap()
}
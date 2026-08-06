package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import com.dshatz.pdfmp.imagebuffer.ImageBuffer

internal expect fun ImageBuffer.toImageBitmap(): ImageBitmap

internal expect val bufferColorFilter: ColorFilter


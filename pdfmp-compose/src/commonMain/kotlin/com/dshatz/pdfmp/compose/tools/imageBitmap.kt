package com.dshatz.pdfmp.compose.tools

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import com.dshatz.pdfmp.ConsumerBuffer

internal expect fun ConsumerBuffer.toImageBitmap(): ImageBitmap

internal expect val bufferColorFilter: ColorFilter
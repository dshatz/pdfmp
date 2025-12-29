package com.dshatz.pdfmp.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.dshatz.pdfmp.ConsumerBuffer
import com.dshatz.pdfmp.compose.tools.RecyclableBitmap
import com.dshatz.pdfmp.compose.tools.toImageBitmap
import com.dshatz.pdfmp.model.PageTransform


interface ICurrentImage {
    val requestedTransforms: List<PageTransform>
    val loadedTransforms: List<PageTransform>
    val buffer: ConsumerBuffer
}


data class CurrentImage(
    override val requestedTransforms: List<PageTransform>,
    override val loadedTransforms: List<PageTransform>,
    override val buffer: ConsumerBuffer,
    private val bitmap: MutableState<RecyclableBitmap?> = mutableStateOf(null)
): ICurrentImage {
    fun free() {
        buffer.free()
    }

    @Composable
    fun composeBitmap(): RecyclableBitmap {
        return bitmap.value ?: toImageBitmap().also {
            bitmap.value = it
        }
    }
}
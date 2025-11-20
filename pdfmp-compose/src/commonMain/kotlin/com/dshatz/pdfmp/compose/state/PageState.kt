package com.dshatz.pdfmp.compose.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.dshatz.pdfmp.ImageTransform
import com.dshatz.pdfmp.compose.viewport
import kotlin.math.min

data class PdfPageState(
    val docState: PdfDocumentState,
    val verticalOffset: MutableState<Float>,
    val imageSize: MutableState<Size> = mutableStateOf(Size(1f, 1f))
) {

    val offsetState: State<Offset> = derivedStateOf {
        Offset(docState.globalHorizontalOffset.value, verticalOffset.value)
    }
    val requestedTransform: State<ImageTransform> = derivedStateOf {
        ImageTransform(
            docState.scale.value,
            offsetState.value.x,
            offsetState.value.y,
            min(docState.viewport.value.width, imageSize.value.width).toInt(),
            min(docState.viewport.value.height, imageSize.value.height).toInt()
        )
    }

    fun dispatchScroll(offset: Offset): Offset {
        val coerced = requestedTransform.value.let {
            Offset(
                x = it.offsetX + offset.x,
                y = it.offsetY + offset.y
            ).coerceOffset()
        }
        docState.globalHorizontalOffset.value = coerced.x
        verticalOffset.value = coerced.y
        return offset - coerced
    }
    private fun Offset.coerceOffset(): Offset {
        val image = imageSize.value

        val viewport = requestedTransform.value.viewport
        val maxX = 0f
        val minX = (viewport.width - image.width).coerceAtMost(0f)

        val maxY = 0f
        val minY = (viewport.height - image.height).coerceAtMost(0f)
        val coerced = Offset(
            x = x.coerceIn(minX, maxX),
            y = y.coerceIn(minY, maxY)
        )
        return coerced
    }

    fun setImageSize(size: Size) {
        this.imageSize.value = size
    }
}
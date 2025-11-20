package com.dshatz.pdfmp.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.dshatz.pdfmp.*
import com.dshatz.pdfmp.compose.state.PdfPageState
import com.dshatz.pdfmp.compose.tools.PartialBitmapRenderer
import com.dshatz.pdfmp.compose.tools.pageTransformModifier
import com.dshatz.pdfmp.compose.tools.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

val ImageTransform.viewport get() = IntSize(viewportWidth, viewportHeight)

@Composable
internal fun InternalPdfPage(
    renderer: PdfRenderer,
    state: PdfPageState,
    page: Int,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true
) {
    /**
     * The best image we have now.
     */
    var displayedImage by remember { mutableStateOf<CurrentImage?>(null) }

    val requestedTransform by state.requestedTransform

    val ratio by produceState(1f, renderer, page) {
        value = renderer.getAspectRatio(page)
    }

    LaunchedEffect(requestedTransform) {
        // Only re-render if the requested transform has changed
        if (requestedTransform != displayedImage?.loadedTransform) {
            if (displayedImage != null) delay(100)

            withContext(Dispatchers.IO) {
                val renderResult = renderer.render(
                    RenderRequest(
                        page,
                        requestedTransform,
                        0
                    )
                ) as PdfRenderer.JvmRenderResponse
                displayedImage = CurrentImage(
                    requestedTransform,
                    renderResult.transform,
                    renderResult.pageSize,
                    renderResult
                )
            }
        }
    }

    val viewportSize by state.docState.viewport
    val density = LocalDensity.current


    val imageSize by remember(viewportSize, requestedTransform.scale) {
        derivedStateOf {
            val width = viewportSize.width * requestedTransform.scale
            val height = width / ratio
            Size(
                width = width,
                height = height
            )
        }
    }


    val displaySize by remember(imageSize, viewportSize) {
        derivedStateOf {
            Size(
                width = min(imageSize.width, viewportSize.width),
                height = min(imageSize.height, viewportSize.height)
            )
        }
    }

    val baseImage by produceState<CurrentImage?>(null, renderer, page, viewportSize, ratio) {
        delay(50)
        val transform = ImageTransform(
            1f,
            0f,
            0f,
            viewportSize.width.toInt(),
            (viewportSize.width / ratio).toInt()
        )
        println("Requesting baseImage: ${transform.viewport}")
        val renderResult =
            withContext(Dispatchers.IO) { renderer.render(RenderRequest(page, transform, 0)) }
        value = CurrentImage(
            transform,
            transform,
            renderResult.pageSize,
            renderResult
        )
    }


    val scrollState = rememberScrollableState {
        state.dispatchScroll(Offset(0f, it)).y
    }
    val draggableVertical = rememberDraggableState {
        val unconsumed = state.dispatchScroll(Offset(0f, it))
    }

    val draggableHorizontal = rememberDraggableState {
        state.dispatchScroll(Offset(it, 0f))
    }

    val imageSizeDp by remember(imageSize, displaySize) {
        derivedStateOf {
            with(density) { imageSize.toDpSize() }
        }
    }




    // The viewport - sized by the incoming modifier.
    Box(
        modifier.then(Modifier.onGloballyPositioned {
            state.docState.setViewport(
                Size(
                    it.size.width.toFloat(),
                    it.size.height.toFloat()
                )
            )
        }
            .clipToBounds()
            .scrollable(scrollState, Orientation.Vertical, enabled = scrollable)
            .draggable(draggableHorizontal, orientation = Orientation.Horizontal)
            .draggable(draggableVertical, orientation = Orientation.Vertical, enabled = scrollable)
            .pageTransformModifier(state, scrollable))
    ) {

        baseImage?.let { baseImg ->
            val image = baseImg.toImageBitmap()
            DisposableEffect(image) {
                onDispose {
                    image.onRecycle()
                }
            }
            PartialBitmapRenderer(
                bitmap = baseImg.toImageBitmap().imageBitmap,
                srcOffset = requestedTransform.offset.let {
                    IntOffset(
                        x = (-it.x / requestedTransform.scale).toInt(),
                        y = (-it.y / requestedTransform.scale).toInt()
                    )
                },
                scale = requestedTransform.scale,
                dstSize = displaySize,
                modifier = Modifier.requiredSize(with(density) { displaySize.toDpSize() }).clipToBounds()
            )
        }
        Box(
            Modifier
                .requiredSize(imageSizeDp)
                .border(Dp.Hairline, Color.Black)
        ) {}


        // The image itself.
        displayedImage?.let { currentImg ->
            val loadedTransform = currentImg.loadedTransform

            val relativeScale =
                if (loadedTransform.scale != requestedTransform.scale) requestedTransform.scale / loadedTransform.scale else 1f

            val relativeX = requestedTransform.offsetX - (loadedTransform.offsetX * relativeScale)
            val relativeY = requestedTransform.offsetY - (loadedTransform.offsetY * relativeScale)

            LaunchedEffect(imageSize) {
                state.setImageSize(imageSize)
            }

            val image = currentImg.toImageBitmap()
            DisposableEffect(image) {
                onDispose {
                    image.onRecycle()
                }
            }

            Image(
                bitmap = image.imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.None,
                alignment = Alignment.TopStart,
                modifier = Modifier.requiredSize(with(density) { viewportSize.toDpSize() })
                    .graphicsLayer {
                        scaleX = relativeScale
                        scaleY = relativeScale
                        translationX = relativeX
                        translationY = relativeY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }


        /*Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DebugBadge("Viewport: ${viewportSize.width} x ${viewportSize.height}")
            DebugBadge("Image size: $imageSize")
        }*/
    }


}

@Composable
private fun ColumnScope.DebugBadge(text: String) {
    ElevatedCard {
        Text(text, modifier = Modifier.padding(10.dp))
    }
}

val ImageTransform.offset get() = Offset(offsetX, offsetY)


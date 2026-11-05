@file:OptIn(InternalPdfmpApi::class)

package com.dshatz.pdfmp.gldemo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dshatz.pdfmp.InitLib
import com.dshatz.pdfmp.InternalPdfmpApi
import com.dshatz.pdfmp.PageDimensions
import com.dshatz.pdfmp.PdfRenderer
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.delay
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Drawable
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL11
import java.io.File
import kotlin.system.measureNanoTime

private const val GL_TEXTURE_2D = 0x0DE1
private const val GL_RGBA8 = 0x8058

/**
 * Prototype: pdfium renders straight into a persistently mapped GL pixel buffer, which is copied into a texture on
 * the GPU and adopted by Skia, instead of pdfium -> heap -> Skia raster upload.
 *
 * Compose's draw callbacks, and even Skiko's render delegate, only record into pictures: their canvas has no GPU
 * context. A recorded Drawable is called back when the picture is replayed on the real surface, with the GL context
 * current and a GPU-backed canvas, so the GL work happens there.
 *
 * Run: `./gradlew :desktopapp:runGlDemo` (needs freshCompose, see local.properties).
 * Args: [pdf path] [--exit] (exit after the timings are printed, or after 25 s).
 */
fun main(args: Array<String>) {
    val pdf = args.firstOrNull { !it.startsWith("--") }
        ?: "sample-shared/src/commonMain/composeResources/files/sample.pdf"
    val exit = "--exit" in args
    application {
        Window(onCloseRequest = ::exitApplication, title = "pdfium -> GL texture") {
            println("[demo] compose render api: ${window.renderApi}")
            GlDemo(File(pdf).readBytes(), exit, ::exitApplication)
        }
    }
}

private class DemoState {
    @Volatile var wanted: PageDimensions? = null
    @Volatile var gl: GlPixelBuffer? = null
    @Volatile var rendered = false
    @Volatile var image: Image? = null
    @Volatile var failed = false
    @Volatile var heapPixmap: Pixmap? = null
    @Volatile var width = 0f
    @Volatile var height = 0f
    var framesWithImage = 0
}

/** Recorded by Compose, executed on replay with the real GPU canvas. */
private class GlDrawable(private val state: DemoState) : Drawable() {
    override fun onDraw(canvas: SkCanvas?) {
        canvas ?: return
        onSurfaceRender(state, canvas)
        val image = state.image ?: return
        canvas.drawImageRect(image, Rect(0f, 0f, state.width, state.height))
    }

    override fun onGetBounds() = Rect(0f, 0f, 16384f, 16384f)
}

@Composable
private fun GlDemo(bytes: ByteArray, exitWhenDone: Boolean, exit: () -> Unit) {
    val state = remember { DemoState() }
    val drawable = remember { GlDrawable(state) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick = it }
    }

    if (exitWhenDone) LaunchedEffect(Unit) {
        delay(25_000)
        println("[demo] giving up after 25 s")
        exit()
    }

    LaunchedEffect(Unit) {
        InitLib.init()
        val renderer = PdfRenderer(PdfSource.PdfBytes(bytes))
        renderer.openDocument().getOrThrow()
        val ratio = renderer.getPageRatio(0)
        val width = 1200
        val dims = PageDimensions(width, (width * ratio).toInt())
        println("[demo] document open, page 0 ratio=$ratio, rendering at ${dims.width}x${dims.height}")

        state.wanted = dims
        while (state.gl == null) {
            if (state.failed) return@LaunchedEffect
            withFrameNanos { }
        }
        val gl = state.gl!!

        val heap = heapBuffer(dims)
        state.heapPixmap = heap.pixmap

        suspend fun time(name: String, buffer: ImageBuffer) {
            renderer.render(0, dims, buffer) // warm up
            val ms = (0 until 15).map { measureNanoTime { renderer.render(0, dims, buffer) } / 1e6 }
            println("render %-22s avg %.2f ms  min %.2f ms".format(name, ms.average(), ms.min()))
        }
        time("into heap", heap)
        time("into mapped GL buffer", gl.imageBuffer)
        state.rendered = true
    }

    Canvas(Modifier.fillMaxSize()) {
        tick // redraw every frame
        state.width = size.width
        state.height = size.height
        drawIntoCanvas { composeCanvas ->
            composeCanvas.skiaCanvas.drawDrawable(drawable)
            if (exitWhenDone && state.image != null && ++state.framesWithImage == 5) exit()
        }
    }
}

/** On replay: GL context current, [canvas] backed by the window surface. */
private fun onSurfaceRender(state: DemoState, canvas: SkCanvas) {
    if (state.failed) return
    val ctx = canvas.recordingContext
    if (ctx == null) {
        println("[demo] replay canvas has no recordingContext (canvas surface: ${canvas.surface})")
        state.failed = true
        return
    }
    try {
        val dims = state.wanted
        if (state.gl == null && dims != null) {
            state.gl = GlPixelBuffer(dims.width, dims.height, readable = true)
            println("[demo] GL renderer: ${GL11.glGetString(GL11.GL_RENDERER)}")
            ctx.resetGLAll()
        }
        val gl = state.gl ?: return
        if (!state.rendered || state.image != null) return

        // Baseline: Skia's own raster upload of the heap copy, forced to finish, on a throwaway surface.
        val target = canvas.surface?.makeSurface(ImageInfo.makeN32Premul(gl.width, gl.height))
        if (target != null) {
            val raster = Image.makeFromPixmap(state.heapPixmap!!)
            val rasterMs = measureNanoTime {
                target.canvas.drawImageRect(raster, Rect(0f, 0f, gl.width.toFloat(), gl.height.toFloat()))
                ctx.flush()
                ctx.submit(true)
            } / 1e6
            raster.close()
            target.close()
            println("baseline: draw heap image incl. Skia upload  %.2f ms".format(rasterMs))
        }

        val uploadMs = measureNanoTime {
            gl.upload()
            GL11.glFinish()
        } / 1e6
        println("PBO -> texture upload (GPU copy)            %.2f ms".format(uploadMs))

        val backend = BackendTexture.makeGL(gl.width, gl.height, false, gl.texture, GL_TEXTURE_2D, GL_RGBA8)
        state.image = Image.adoptTextureFrom(
            ctx, backend, SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888, ColorAlphaType.PREMUL,
        )
        ctx.resetGLAll()
        println("[demo] texture adopted by Skia")
    } catch (t: Throwable) {
        state.failed = true
        println("[demo] GL path failed:")
        t.printStackTrace(System.out)
    }
}

private fun heapBuffer(dims: PageDimensions): ImageBuffer {
    val stride = dims.width * 4
    val data = Data.makeUninitialized(stride * dims.height)
    val pixmap = Pixmap.make(
        ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, null), dims.width, dims.height),
        data,
        stride,
    )
    return ImageBuffer(pixmap)
}

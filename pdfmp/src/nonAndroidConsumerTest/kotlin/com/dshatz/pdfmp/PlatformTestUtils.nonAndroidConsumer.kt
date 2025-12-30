package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.BufferDimensions
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.haveMessage
import org.jetbrains.skia.Color
import org.jetbrains.skia.Pixmap

actual object PlatformTestUtils {

    private val pixmaps = mutableMapOf<ConsumerBuffer, Pixmap>()
    actual fun bufferAssertions(buffer: ConsumerBuffer, dimensions: BufferDimensions) {
        buffer.skiaBitmap.isEmpty shouldBe false
        buffer.skiaBitmap.isOpaque shouldBe false
        buffer.skiaBitmap.isClosed shouldBe false
        buffer.skiaBitmap.isReadyToDraw shouldBe true
//        buffer.skiaBitmap.peekPixels()!!.rowBytes shouldBe dimensions.stride
    }

    actual fun pixelShouldBeTransparent(buffer: ConsumerBuffer, x: Int, y: Int) {
        val pixel = buffer.getPixel(x, y)
        withClue("Expected pixel $x, $y to be transparent") {
            pixel shouldBe Color.TRANSPARENT
        }
    }

    actual fun pixelShouldBeWhite(buffer: ConsumerBuffer, x: Int, y: Int) {
        val pixel = buffer.getPixel(x, y)
        withClue("Expected pixel $x, $y to be white") {
            pixel shouldBe Color.WHITE
        }
    }

    private fun ConsumerBuffer.getPixel(x: Int, y: Int): Int {
        val pixels = pixmaps.getOrPut(this) {
            skiaBitmap.peekPixels()!!
        }
        return pixels.getColor(
            x,
            y
        )
    }
}
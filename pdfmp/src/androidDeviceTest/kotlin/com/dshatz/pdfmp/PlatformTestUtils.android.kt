package com.dshatz.pdfmp

import android.graphics.Color
import com.dshatz.pdfmp.model.BufferDimensions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

actual object PlatformTestUtils {
    actual fun bufferAssertions(buffer: ConsumerBuffer, dimensions: BufferDimensions) {
        buffer.dimensions shouldBe dimensions
        buffer.androidBitmap.isRecycled shouldBe false
    }

    actual fun pixelShouldBeTransparent(buffer: ConsumerBuffer, x: Int, y: Int) {
        buffer.androidBitmap.getPixel(x, y) shouldBe Color.TRANSPARENT
    }

    actual fun pixelShouldBeWhite(buffer: ConsumerBuffer, x: Int, y: Int) {
        buffer.androidBitmap.getPixel(x, y) shouldNotBe Color.TRANSPARENT
    }
}
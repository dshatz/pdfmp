package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.BufferDimensions

expect object PlatformTestUtils {
    fun bufferAssertions(
        buffer: ConsumerBuffer,
        dimensions: BufferDimensions
    )

    fun pixelShouldBeTransparent(buffer: ConsumerBuffer, x: Int, y: Int)

    fun pixelShouldBeWhite(buffer: ConsumerBuffer, x: Int, y: Int)
}
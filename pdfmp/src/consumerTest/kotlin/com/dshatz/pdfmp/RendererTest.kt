package com.dshatz.pdfmp

import com.dshatz.pdfmp.Const.EXPECTED_PAGE_COUNT
import com.dshatz.pdfmp.PlatformTestUtils.bufferAssertions
import com.dshatz.pdfmp.PlatformTestUtils.pixelShouldBeTransparent
import com.dshatz.pdfmp.PlatformTestUtils.pixelShouldBeWhite
import com.dshatz.pdfmp.model.*
import com.dshatz.pdfmp.source.PdfSource
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

val rendererTests by testSuite {
    testFixture {
        InitLib().init()
    } asContextForAll {
        test("get page count") {
            val renderer = initWithSamplePdf()
            val pageCount = renderer.getPageCount().getOrThrow()
            pageCount shouldBe EXPECTED_PAGE_COUNT
        }

        test("get page ratios") {
            val renderer = initWithSamplePdf()
            val ratios = renderer.getPageRatios().getOrThrow()
            ratios.size shouldBe EXPECTED_PAGE_COUNT
            ratios shouldBe generateSequence { 0.77272725f }.take(ratios.size).toList()
        }
        test("render one whole page") {
            val renderer = initWithSamplePdf()
            val dimensions = BufferDimensions(1000, 1000, 1000*4)
            val buffer = getBuffer(dimensions)
            buffer.withAddress { address ->
                address shouldNotBe 0
                val req = RenderRequest(
                    listOf(
                        PageTransform(
                            0,
                            0,
                            0,
                            0,
                            0,
                            1000,
                            1000,
                            0,
                            1f
                        )
                    ),
                    0,
                    0,
                    BufferInfo(BufferDimensions(1000, 1000, 1000 * 4), address)
                )
                val result = renderer.render(req)
                val response = result.getOrThrow()
                response.transforms shouldBe req.transforms

                bufferAssertions(buffer, dimensions)
                pixelShouldBeWhite(buffer, 0, 0)
                pixelShouldBeWhite(buffer, 0, 999)
                pixelShouldBeWhite(buffer, 999, 0)
                pixelShouldBeWhite(buffer, 999, 999)
            }
        }


        test("render stacked pages for viewport") {
            val renderer = initWithSamplePdf()
            val dimensions = BufferDimensions(1000, 1000, 1000*4)
            val buffer = getBuffer(dimensions)
            val gap = 100
            val pageSize = 600
            buffer.withAddress { address ->
                address shouldNotBe 0
                val req = RenderRequest(
                    listOf(
                        PageTransform(
                            0,
                            0,
                            0,
                            0,
                            0,
                            pageSize,
                            pageSize,
                            0,
                            1f
                        ),
                        PageTransform(
                            1,
                            1000 - pageSize - gap,
                            0,
                            0,
                            0,
                            pageSize,
                            pageSize,
                            gap,
                            1f
                        )
                    ),
                    0,
                    0,
                    BufferInfo(BufferDimensions(1000, 1000, 1000 * 4), address)
                )
                val result = renderer.render(req)
                val response = result.getOrThrow()
                response.transforms shouldBe req.transforms

                bufferAssertions(buffer, dimensions)
                pixelShouldBeTransparent(buffer, 0, pageSize) // gap
                pixelShouldBeWhite(buffer, 0, pageSize - 1) // still first page
                pixelShouldBeWhite(buffer, 0, 0)
                pixelShouldBeWhite(buffer, 0, pageSize + gap) // Second page
            }
        }
    }
}


private object Const {
    const val EXPECTED_PAGE_COUNT = 9
}

private suspend fun initWithSamplePdf(): PdfRenderer {
    return PdfRendererFactory.createFromSource(
        PdfSource.PdfBytes(readResource("sample.pdf"))
    ).getOrElse { fail("Failed to initialize PdfRenderer: $it") }
}

private fun getBuffer(dimensions: BufferDimensions): ConsumerBuffer {
    return ConsumerBufferUtil.allocate(
        (dimensions.width * dimensions.height * 4).bytes,
        dimensions.width,
        dimensions.height,
    )
}
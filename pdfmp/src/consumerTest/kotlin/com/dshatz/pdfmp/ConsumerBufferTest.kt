package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.SizeB
import com.dshatz.pdfmp.model.bytes
import com.dshatz.pdfmp.model.calculateSize
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

val consumerBufferTest by testSuite {
    testFixture {
        InitLib().init()
    } asContextForAll {
        test("get for page") {
            val pool = ConsumerBufferPool()
            pool.totalBufferMemory shouldBe SizeB.ZERO

            val imageSize = 1000
            val bufferSize = (imageSize * imageSize * 4).bytes
            val transform = makeSquareTransform(0, imageSize)

            val buffer = pool.getBufferPage(transform)
            buffer.withAddress {
                it.toULong() shouldBeGreaterThan 0u
            }
            pool.totalBufferMemory shouldBe bufferSize
            buffer.free()
            // free() doesnt clear anything, just marks it for reuse.
            pool.totalBufferMemory shouldBe bufferSize

            // Now try to reuse
            val newBuffer = pool.getBufferPage(transform)
            withClue("Buffer with same parameters was supposed to be reused but wasn't") {
                newBuffer shouldBe buffer
            }
            pool.totalBufferMemory shouldBe bufferSize
        }

        test("get for viewport") {
            val pool = ConsumerBufferPool()
            pool.totalBufferMemory shouldBe 0L.bytes
            val pageSize = 1000
            val pageCount = 4
            val gap = 100
            val transforms = (0..<pageCount).map {
                makeSquareTransform(
                    it,
                    pageSize,
                    if (it != 0 && it != pageCount - 1) gap else 0
                )
            }
            val buffer = pool.getBufferViewport(transforms)
            pool.bufferViewport shouldBe buffer
            buffer.capacity() shouldBe transforms.calculateSize().let { it.first * it.second * 4 }.bytes

            // Request again
            val bufferSame = pool.getBufferViewport(transforms)
            bufferSame shouldBe buffer

            // Now test reusing of buffer if the viewport got smaller
            val transformsSmaller = (0..<pageCount).map {
                makeSquareTransform(
                    it,
                    pageSize - 1,
                    if (it != 0 && it != pageCount - 1) gap else 0
                )
            }
            val smallerBuffer = pool.getBufferViewport(transformsSmaller)
            smallerBuffer shouldBe buffer


            // Now test not reusing of buffer if the viewport got larger
            val transformsLarger = (0..<pageCount).map {
                makeSquareTransform(
                    it,
                    pageSize + 1,
                    if (it != 0 && it != pageCount - 1) gap else 0
                )
            }
            val largerBuffer = pool.getBufferViewport(transformsLarger)
            largerBuffer shouldNotBe buffer

            withClue("Old viewport should have been freed after replacement") {
                buffer.isFree shouldBe true
            }
        }
    }
}

private fun makeSquareTransform(
    idx: Int,
    size: Int,
    gap: Int = 0
): PageTransform {
    return PageTransform(
        idx,
        0,
        0,
        0,
        0,
        size,
        size,
        gap,
        1f
    )
}
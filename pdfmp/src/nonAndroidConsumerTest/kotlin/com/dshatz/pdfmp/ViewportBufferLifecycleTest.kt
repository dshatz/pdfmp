package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.SizeB
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * Regression tests for two viewport-buffer defects that killed apps in the field with
 * `IllegalStateException: Could not read bitmap buffer address` (and, for the dispose race,
 * native crashes in SkBitmap::peekPixels):
 *
 * 1. [ConsumerBufferUtil.allocate] ignored `allocPixels`' result — a failed allocation (memory
 *    pressure, oversized viewport at deep zoom) handed out a pixel-less bitmap that only failed
 *    later, inside a render, with a misleading error.
 * 2. [ConsumerBufferPool.getBufferViewport] disposed the previous viewport buffer inline when the
 *    viewport grew, with no coordination with an in-flight render still holding it or with the
 *    displayed image referencing its pixels zero-copy (extractSubset). Buffers are now retired
 *    instead and disposed on a later request, once freed by their consumer.
 */
val viewportBufferLifecycleTest by testSuite {
    testFixture {
        InitLib().init()
    } asContextForAll {
        test("allocation failure fails fast with a descriptive error") {
            // Dimensions whose byte size overflows: Skia rejects the allocation. The failure must
            // surface here, at the cause — not later as a buffer-address error inside a render.
            val failure = shouldThrow<IllegalStateException> {
                ConsumerBufferUtil.allocate(
                    SizeB(Int.MAX_VALUE.toLong() * Int.MAX_VALUE * 4L),
                    Int.MAX_VALUE,
                    Int.MAX_VALUE,
                )
            }
            failure.message shouldStartWith "Failed to allocate"
        }

        test("growing the viewport keeps the previously handed-out buffer alive") {
            val pool = ConsumerBufferPool()
            // Render A acquires the viewport buffer...
            val heldByRenderA = pool.getBufferViewport(listOf(squareTransform(100)))
            // ...then the viewport grows (zoom/rotation) while render A still holds it.
            pool.getBufferViewport(listOf(squareTransform(200)))

            withClue("a buffer still handed out to a render must not be disposed") {
                heldByRenderA.skiaBitmap.isClosed shouldBe false
            }
            heldByRenderA.withAddress {
                it.toULong() shouldBeGreaterThan 0u
            }
            withClue("the replaced buffer is retired until its consumer frees it") {
                pool.retiredViewportBuffers shouldContainExactly listOf(heldByRenderA)
            }
        }

        test("retired viewport buffer is disposed once freed and a later request arrives") {
            val pool = ConsumerBufferPool()
            val old = pool.getBufferViewport(listOf(squareTransform(100)))
            pool.getBufferViewport(listOf(squareTransform(200)))

            // The consumer releases the replaced buffer (PdfViewport frees the previous image)...
            old.free()
            // ...and the next pool request sweeps retired buffers: no use-after-free, no leak.
            pool.getBufferViewport(listOf(squareTransform(200)))
            withClue("freed retired buffer must be disposed on the next request") {
                old.skiaBitmap.isClosed shouldBe true
                pool.retiredViewportBuffers shouldBe emptyList()
            }
        }

        test("handed-out viewport buffer is marked unfree") {
            val pool = ConsumerBufferPool()
            val buffer = pool.getBufferViewport(listOf(squareTransform(100)))
            withClue("a handed-out buffer must be marked unfree until released") {
                buffer.isFree shouldBe false
            }
        }
    }
}

private fun squareTransform(size: Int): PageTransform {
    return PageTransform(
        0,
        0,
        0,
        0,
        0,
        size,
        size,
        0,
        1f
    )
}

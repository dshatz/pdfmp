package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.PageTransform
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val pageTransformTest by testSuite {
    test("uncut transform") {
        val size = 1000
        val transform = PageTransform(
            0,
            100,
            100,
            200,
            200,
            size,
            size,
            0,
            1f
        )

        transform.sliceSize() shouldBe ((size - 400) to (size - 200))

        transform.uncut().apply {
            topCutoff shouldBe 0
            bottomCutoff shouldBe 0
            leftCutoff shouldBe 0
            rightCutoff shouldBe 0
        }

        transform.uncut().sliceSize() shouldBe (size to size)
    }
}
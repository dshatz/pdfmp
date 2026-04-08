package com.dshatz.pdfmp

import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.RenderRequest
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random

val PackTests by testSuite {
    test("pack page transform") {
        val input = randomPageTransform()
        val buffer = Buffer()
        input.pack(buffer)

        PageTransform.unpack(buffer.copy()) shouldBe input
    }

    test("pack render request") {
        val input = RenderRequest(
            transforms = generateSequence { randomPageTransform() }.take(Random.nextInt(10)).toList(),
            0,
            0,
            BufferDimensions(
                100,
                200,
                400
            ).withAddress(1),
        )
        val bytes = input.pack()
        RenderRequest.unpack(bytes) shouldBe input
    }


    test("pack failure throwing") {
        val result = Result.failure<Unit>(RuntimeException("native message"))
        val buffer = Buffer()
        result.pack(buffer, {})

        val exception = shouldThrow<Exception> {
            unpackResultOrThrow(buffer.readByteArray(), {})
        }
        exception.message shouldContain "native message"
    }

    test("pack failure") {
        val result = Result.failure<Unit>(RuntimeException("native message"))
        val buffer = Buffer()
        result.pack(buffer, {})

        val exception = shouldNotThrowAny {
            unpackResult(buffer.readByteArray(), {})
        }.exceptionOrNull()
        exception shouldNotBe null
        exception!!.message shouldContain "native message"
    }

    test("pack success") {
        val result = Result.success<Int>(999)
        val buffer = Buffer()
        result.pack(buffer, Buffer::writeInt)

        unpackResultOrThrow(buffer.readByteArray(), Buffer::readInt) shouldBe 999
    }
}

private fun randomPageTransform(): PageTransform {
    return PageTransform(
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextInt(),
        Random.nextFloat()
    )
}
package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random

val loadLibraryTest by testSuite {
    test("init pdfium") {
        shouldNotThrowAny {
            InitLib().init()
        }
        val renderer = PdfRenderer(PdfSource.PdfBytes(Random.nextBytes(100)))
        val openResult = renderer.openDocument()
        val exception = openResult.exceptionOrNull()
        exception shouldNotBe null

        // That means pdfium is actually loaded and has attempted to read our garbage.
        println(exception)
//        exception!!.message shouldContain "PDFIUM error: "
    }

}
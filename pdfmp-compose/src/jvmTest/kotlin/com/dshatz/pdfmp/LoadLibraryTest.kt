package com.dshatz.pdfmp

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

val loadLibraryTest by testSuite {
    test("init pdfium") {
        shouldNotThrowAny {
            InitLib.init()
        }
        val renderer = PdfData.bytes(Random.nextBytes(100)).createRenderer(noPlatformContext!!)
        val openResult = renderer.openDocument()
        val exception = openResult.exceptionOrNull()
        exception shouldNotBe null

        // That means pdfium is actually loaded and has attempted to read our garbage.
        println(exception)
//        exception!!.message shouldContain "PDFIUM error: "
    }

}
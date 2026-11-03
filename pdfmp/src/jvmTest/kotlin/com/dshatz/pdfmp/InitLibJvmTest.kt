package com.dshatz.pdfmp

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

val initLibJvmTest by testSuite {
    test("each library is extracted once per process") {
        val initLib = InitLib()
        val pdfium = initLib.loadLibraryFromJar("pdfium")
        val pdfmp = initLib.loadLibraryFromJar("pdfmp")

        shouldNotThrowAny {
            InitLib().init()
            InitLib().init()
        }

        // A new InitLib is created for every opened document; it must reuse the same extracted files.
        InitLib().loadLibraryFromJar("pdfium") shouldBeSameInstanceAs pdfium
        InitLib().loadLibraryFromJar("pdfmp") shouldBeSameInstanceAs pdfmp
        pdfium.exists().shouldBeTrue()
        pdfium.parentFile.listFiles()!!.size shouldBe 1
    }
}

package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random

// The natives must be bundled and loadable from this module's own runtime (e.g. the Android test APK).
val loadLibraryTest by testSuite {
    test("init pdfium") {
        shouldNotThrowAny { InitLib.init() }
        PdfRenderer(PdfSource.PdfBytes(Random.nextBytes(100))).use { renderer ->
            val exception = renderer.openDocument().exceptionOrNull()
            exception shouldNotBe null
            // pdfium is actually loaded and has attempted to read our garbage.
            exception!!.message!! shouldContain "PDFIUM error"
        }
    }
}

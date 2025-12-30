package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random

class LoadLibraryTest: FunSpec({
    test("init pdfium") {
        shouldNotThrowAny {
            InitLib().init()
        }
        val openResult = PdfRendererFactory.createFromSource(PdfSource.PdfBytes(Random.nextBytes(100)))
        val exception = openResult.exceptionOrNull()
        exception shouldNotBe null

        // That means pdfium is actually loaded and has attempted to read our garbage.
        println(exception)
        exception!!.message shouldContain "PDFIUM error: "
    }

})
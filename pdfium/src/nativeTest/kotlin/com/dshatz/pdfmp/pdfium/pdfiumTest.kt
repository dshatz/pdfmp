package com.dshatz.pdfmp.pdfium

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val pdfiumTest by testSuite {

    test("Init") {
        Pdfium.InitLibrary()
    }

    test("Load from path") {
        val doc = Pdfium.LoadDocument("../sample-shared/src/commonMain/composeResources/files/sample.pdf", null)!!
        Pdfium.GetPageCount(doc) shouldBe 9
    }
}
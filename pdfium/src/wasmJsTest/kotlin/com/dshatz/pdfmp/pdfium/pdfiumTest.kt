package com.dshatz.pdfmp.pdfium

import de.infix.testBalloon.framework.core.testSuite

val pdfiumTest by testSuite {
    test("Init") {
        awaitPdfium()
        Pdfium.InitLibrary()
    }
}
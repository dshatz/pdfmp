package com.dshatz.pdfmp

import com.dshatz.pdfmp.source.PdfSource
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

// Only what differs per platform: loading the native libs and crossing the JNI/native boundary.
// PDF below doesnt have a xref table. Pdfium ignores that.
private val ONE_PAGE_PDF = """
    %PDF-1.4
    1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
    2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
    3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 100]>>endobj
    trailer<</Root 1 0 R/Size 4>>
    %%EOF
""".trimIndent().encodeToByteArray()

val nativeLibraryTest by testSuite {
    test("libraries load and pdfium opens a document") {
        InitLib.init()
        PdfRenderer(PdfSource.PdfBytes(ONE_PAGE_PDF)).use { renderer ->
            renderer.openDocument().getOrThrow()
            renderer.getPageCount() shouldBe 1
            renderer.getPageRatio(0) shouldBeGreaterThan 0f
        }
    }

    test("pdfium errors reach Kotlin") {
        InitLib.init()
        PdfRenderer(PdfSource.PdfBytes(ByteArray(100) { it.toByte() })).use { renderer ->
            val error = renderer.openDocument().exceptionOrNull()
            error shouldNotBe null
            error!!.message!! shouldContain "PDFIUM error"
        }
    }
}

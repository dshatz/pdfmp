package com.dshatz.pdfmp.pdfium

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned

private const val SIZE = 100
private const val RED_SQUARE = "1 0 0 rg 0 0 50 50 re f"
private val RED = listOf(0, 0, 255, 255) // BGRA
private val WHITE = listOf(255, 255, 255, 255)

/** An empty 100x100 page whose only content is a stamp with a red appearance stream, covering 25..75 on both axes. */
private fun stampPdf(): ByteArray {
    val objects = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $SIZE $SIZE] /Annots [4 0 R] >>",
        "<< /Type /Annot /Subtype /Stamp /Rect [25 25 75 75] /F 4 /AP << /N 5 0 R >> >>",
        "<< /Type /XObject /Subtype /Form /BBox [0 0 50 50] /Length ${RED_SQUARE.length} >>\nstream\n$RED_SQUARE\nendstream",
    )
    val out = StringBuilder("%PDF-1.7\n")
    val offsets = objects.mapIndexed { i, body ->
        out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") }
    }
    val xref = out.length
    out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toString().encodeToByteArray()
}

/** Renders [stampPdf] with [draw] and returns the BGRA value of the centre pixel. */
@OptIn(ExperimentalForeignApi::class)
private fun centerPixel(draw: (PdfiumBitmap, PageHandle) -> Unit): List<Int> {
    val pdf = stampPdf()
    val pixels = ByteArray(SIZE * SIZE * 4)
    pdf.usePinned { data ->
        Pdfium.InitLibrary()
        val doc = Pdfium.LoadMemDocument(DataPointer(data), pdf.size, null)!!
        val page = Pdfium.LoadPage(doc, 0)!!
        pixels.usePinned {
            val bitmap = Pdfium.BitmapCreateExBGRA(DataPointer(it), SIZE, SIZE, SIZE * 4)!!
            draw(bitmap, page)
            Pdfium.BitmapDestroy(bitmap)
        }
        Pdfium.ClosePage(page)
        Pdfium.CloseDocument(doc)
    }
    return (0..3).map { pixels[(SIZE * 50 + 50) * 4 + it].toInt() and 0xFF }
}

val renderAnnotationsTest by testSuite {
    test("the stamp exists only as an annotation") {
        centerPixel { bitmap, page ->
            Pdfium.BitmapFillRect(bitmap, 0, 0, SIZE, SIZE, 0xFFFFFFFFu)
            Pdfium.RenderPageBitmap(bitmap, page, 0, 0, SIZE, SIZE, 0, 0)
        } shouldBe WHITE
    }

    test("renderPage draws annotations") {
        centerPixel { bitmap, page ->
            PdfiumRenderCore.renderPage(Pdfium, bitmap, page, SIZE, SIZE)
        } shouldBe RED
    }

    test("renderTile draws annotations") {
        centerPixel { bitmap, page ->
            PdfiumRenderCore.renderTile(Pdfium, bitmap, page, 50, 50, SIZE, SIZE, 200, 200, null)
        } shouldBe RED
    }
}

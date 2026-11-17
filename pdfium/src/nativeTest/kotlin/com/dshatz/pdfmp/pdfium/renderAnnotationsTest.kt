package com.dshatz.pdfmp.pdfium

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned

private const val SIZE = 100
private const val RED_SQUARE = "1 0 0 rg 0 0 50 50 re f"
private val RED = listOf(0, 0, 255, 255) // BGRA
private val WHITE = listOf(255, 255, 255, 255)
private const val FPDF_ANNOT = 0x01

private fun stream(content: String) = "<< /Type /XObject /Subtype /Form /BBox [0 0 50 50] /Length ${content.length} >>\nstream\n$content\nendstream"

/**
 * An empty 100x100 page whose only content is [annotations], numbered from object 4. [objects] follow them.
 * [acroForm] is the document's form dictionary.
 */
private fun pdf(annotations: List<String>, objects: List<String>, acroForm: String? = null): ByteArray {
    val annotRefs = annotations.indices.joinToString(" ") { "${it + 4} 0 R" }
    val all = listOf(
        "<< /Type /Catalog /Pages 2 0 R" + (acroForm?.let { " /AcroForm $it" } ?: "") + " >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $SIZE $SIZE] /Annots [$annotRefs] >>",
    ) + annotations + objects
    val out = StringBuilder("%PDF-1.7\n")
    val offsets = all.mapIndexed { i, body ->
        out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") }
    }
    val xref = out.length
    out.append("xref\n0 ${all.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
    out.append("trailer\n<< /Size ${all.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toString().encodeToByteArray()
}

/** A stamp with a red appearance stream, covering 25..75 on both axes. */
private val stampPdf = pdf(
    listOf("<< /Type /Annot /Subtype /Stamp /Rect [25 25 75 75] /F 4 /AP << /N 5 0 R >> >>"),
    listOf(stream(RED_SQUARE)),
)

/**
 * A filled text field at y 40..60, the way form fillers such as pdfcpu leave it: a value, no appearance stream, and
 * `/NeedAppearances` so the viewer draws one.
 */
private val textFieldPdf = pdf(
    listOf("<< /Type /Annot /Subtype /Widget /FT /Tx /T (name) /V (Filled) /Rect [10 40 90 60] /F 4 /DA (/Helv 14 Tf 0 g) >>"),
    listOf("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
    acroForm = "<< /Fields [4 0 R] /NeedAppearances true /DR << /Font << /Helv 5 0 R >> >> /DA (/Helv 0 Tf 0 g) >>",
)

/** A check box covering 25..75 on both axes, in [state]: its `Yes` appearance is red, `Off` is empty. */
private fun checkBoxPdf(state: String) = pdf(
    listOf("<< /Type /Annot /Subtype /Widget /FT /Btn /T (check) /V /$state /AS /$state /Rect [25 25 75 75] /F 4 /AP << /N << /Yes 5 0 R /Off 6 0 R >> >> >>"),
    listOf(stream(RED_SQUARE), stream("")),
    acroForm = "<< /Fields [4 0 R] >>",
)

/** Renders [pdf] with [draw] into a 100x100 BGRA bitmap and returns its pixels. */
@OptIn(ExperimentalForeignApi::class)
private fun render(pdf: ByteArray, draw: (PdfiumBitmap, PageHandle) -> Unit): ByteArray {
    val pixels = ByteArray(SIZE * SIZE * 4)
    pdf.usePinned { data ->
        Pdfium.InitLibrary()
        val doc = Pdfium.InitFormFillEnvironment(Pdfium.LoadMemDocument(DataPointer(data), pdf.size, null)!!)
        val page = Pdfium.LoadPage(doc, 0)!!
        pixels.usePinned {
            val bitmap = Pdfium.BitmapCreateExBGRA(DataPointer(it), SIZE, SIZE, SIZE * 4)!!
            draw(bitmap, page)
            Pdfium.BitmapDestroy(bitmap)
        }
        Pdfium.ClosePage(page)
        Pdfium.CloseDocument(doc)
    }
    return pixels
}

private fun ByteArray.pixel(x: Int, y: Int): List<Int> = (0..3).map { this[(SIZE * y + x) * 4 + it].toInt() and 0xFF }
private fun ByteArray.centerPixel() = pixel(SIZE / 2, SIZE / 2)
private fun ByteArray.nonWhitePixels(rows: IntRange) = rows.sumOf { y -> (0 until SIZE).count { x -> pixel(x, y) != WHITE } }

private val renderPage: (PdfiumBitmap, PageHandle) -> Unit = { bitmap, page ->
    PdfiumRenderCore.renderPage(Pdfium, bitmap, page, SIZE, SIZE)
}

/** The tile at (50, 50) of the page scaled to 200x200: the middle of the page, at twice the size. */
private val renderTile: (PdfiumBitmap, PageHandle) -> Unit = { bitmap, page ->
    PdfiumRenderCore.renderTile(Pdfium, bitmap, page, 50, 50, SIZE, SIZE, 200, 200, null)
}

private val renderPageBitmap = { flags: Int ->
    { bitmap: PdfiumBitmap, page: PageHandle ->
        Pdfium.BitmapFillRect(bitmap, 0, 0, SIZE, SIZE, 0xFFFFFFFFu)
        Pdfium.RenderPageBitmap(bitmap, page, 0, 0, SIZE, SIZE, 0, flags)
    }
}

val renderAnnotationsTest by testSuite {
    test("the stamp exists only as an annotation") {
        render(stampPdf, renderPageBitmap(0)).centerPixel() shouldBe WHITE
    }

    test("renderPage draws annotations") {
        render(stampPdf, renderPage).centerPixel() shouldBe RED
    }

    test("renderTile draws annotations") {
        render(stampPdf, renderTile).centerPixel() shouldBe RED
    }

    test("form fields are not drawn by FPDF_ANNOT alone") {
        render(checkBoxPdf("Yes"), renderPageBitmap(FPDF_ANNOT)).centerPixel() shouldBe WHITE
    }

    test("a filled text field is not drawn by FPDF_ANNOT alone") {
        render(textFieldPdf, renderPageBitmap(FPDF_ANNOT)).nonWhitePixels(40..60) shouldBe 0
    }

    test("renderPage draws the value of a filled text field") {
        render(textFieldPdf, renderPage).nonWhitePixels(40..60) shouldBeGreaterThan 0
    }

    test("renderTile draws the value of a filled text field") {
        // The field's y 40..60 is 80..120 at 200x200, so rows 30..70 of the tile.
        render(textFieldPdf, renderTile).nonWhitePixels(30..70) shouldBeGreaterThan 0
    }

    test("renderPage draws a checked check box") {
        render(checkBoxPdf("Yes"), renderPage).centerPixel() shouldBe RED
    }

    test("renderPage draws an unchecked check box with its Off appearance") {
        render(checkBoxPdf("Off"), renderPage).centerPixel() shouldBe WHITE
    }

    test("renderTile places form fields like the page content") {
        // The check box covers 50..150 at 200x200, which is the whole tile.
        val tile = render(checkBoxPdf("Yes"), renderTile)
        tile.pixel(5, 5) shouldBe RED
        tile.centerPixel() shouldBe RED
        tile.pixel(SIZE - 5, SIZE - 5) shouldBe RED
    }
}

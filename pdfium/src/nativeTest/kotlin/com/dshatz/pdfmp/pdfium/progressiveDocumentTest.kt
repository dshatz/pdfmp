package com.dshatz.pdfmp.pdfium

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.memcpy

private const val SAMPLE = "../sample-shared/src/commonMain/composeResources/files/sample.pdf"
private const val CHUNK = 16 * 1024

@OptIn(ExperimentalForeignApi::class)
private fun readFile(path: String): ByteArray {
    val file = fopen(path, "rb") ?: error("Cannot open $path")
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        fseek(file, 0, SEEK_SET)
        return ByteArray(size).apply { usePinned { fread(it.addressOf(0), 1u, size.convert(), file) } }
    } finally {
        fclose(file)
    }
}

/** Gives pdfium the chunks [availability] reports missing until it reports the data available. */
@OptIn(ExperimentalForeignApi::class)
private fun ProgressiveDocument.load(bytes: ByteArray, fetched: MutableSet<Int>, availability: () -> Availability) {
    while (true) {
        val result = availability()
        if (result.status == Availability.AVAILABLE) return
        result.status shouldBe Availability.NOT_AVAILABLE
        result.missingChunks.shouldNotBeEmpty()
        for (chunk in result.missingChunks) {
            val offset = chunk * CHUNK
            val size = minOf(CHUNK, bytes.size - offset)
            bytes.usePinned { memcpy(chunkAddress(chunk), it.addressOf(offset), size.convert()) }
            chunks.markReceived(offset, size)
            fetched += chunk
        }
    }
}

val progressiveDocumentTest by testSuite {
    test("opens a document and its pages with only the chunks pdfium asks for") {
        Pdfium.InitLibrary()
        val bytes = readFile(SAMPLE)
        val document = ProgressiveDocument(bytes.size, CHUNK)
        val fetched = mutableSetOf<Int>()

        document.load(bytes, fetched) { document.isDocAvail() }
        val doc = document.document() ?: error("No document")
        Pdfium.GetPageCount(doc) shouldBe 9
        val fetchedForDocument = fetched.size

        document.load(bytes, fetched) { document.isPageAvail(0) }
        val page = Pdfium.LoadPage(doc, 0)
        page shouldNotBe null
        Pdfium.GetPageWidthF(page!!) shouldNotBe 0f
        Pdfium.ClosePage(page)

        // Not everything was needed for the document and its first page.
        fetchedForDocument shouldBeLessThan (bytes.size + CHUNK - 1) / CHUNK

        Pdfium.CloseDocument(doc)
        document.destroy()
    }

    test("chunk bookkeeping") {
        val chunks = ProgressiveChunks(length = 100, chunkSize = 10)
        chunks.isAvailable(0, 1) shouldBe false
        chunks.markReceived(10, 20) // chunks 1 and 2
        chunks.isAvailable(10, 20) shouldBe true
        chunks.isAvailable(5, 10) shouldBe false

        val result = chunks.check {
            chunks.request(5, 10) // chunks 0 and 1, 1 arrived
            chunks.request(95, 10) // past the end: chunk 9 only
            Availability.NOT_AVAILABLE
        }
        result.missingChunks shouldBe listOf(0, 9)
        chunks.check { Availability.AVAILABLE }.missingChunks shouldBe emptyList()
    }

    test("partial last chunk") {
        val chunks = ProgressiveChunks(length = 25, chunkSize = 10)
        chunks.markReceived(20, 5)
        chunks.isAvailable(20, 100) shouldBe true // size past the end is clamped
        chunks.isAvailable(19, 2) shouldBe false
    }

    test("zero size is always available") {
        ProgressiveChunks(length = 10, chunkSize = 10).isAvailable(0, 0) shouldBe true
    }

    test("data beyond the end is rejected") {
        shouldThrow<IllegalArgumentException> { ProgressiveChunks(10, 10).markReceived(5, 6) }
    }
}

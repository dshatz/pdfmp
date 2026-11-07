package com.dshatz.pdfmp

import com.sun.net.httpserver.HttpServer
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

private val sampleBytes: ByteArray by lazy {
    Thread.currentThread().contextClassLoader.getResourceAsStream("sample.pdf")!!.use { it.readBytes() }
}

private fun sampleFile(): File = File.createTempFile("sample", ".pdf").apply {
    writeBytes(sampleBytes)
    deleteOnExit()
}

/** Opens [data] like PdfState does, and checks the first and last page of the 9 page sample. */
private suspend fun shouldOpenSample(data: PdfData) {
    InitLib.init()
    val renderer = data.createRenderer(noPlatformContext!!)
    try {
        renderer.openDocument().getOrThrow()
        renderer.getPageCount() shouldBe 9
        renderer.getPageRatio(0) shouldNotBe 0f
        renderer.getPageRatio(8) shouldNotBe 0f
    } finally {
        renderer.close()
    }
}

/** Serves the sample on localhost; with [ranges], answers Range requests with 206 like a static file server. */
private fun <T> withServer(ranges: Boolean, block: (url: String, requests: MutableList<String>) -> T): T {
    val requests = mutableListOf<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/sample.pdf") { exchange ->
        val range = exchange.requestHeaders.getFirst("Range")
        synchronized(requests) { requests += "${exchange.requestMethod} ${range.orEmpty()}" }
        val head = exchange.requestMethod == "HEAD"
        if (ranges && range != null) {
            val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
            val body = sampleBytes.copyOfRange(start, minOf(end, sampleBytes.size - 1) + 1)
            exchange.responseHeaders.add("Content-Range", "bytes $start-${start + body.size - 1}/${sampleBytes.size}")
            exchange.sendResponseHeaders(206, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } else {
            if (head) exchange.responseHeaders.add("Content-Length", sampleBytes.size.toString())
            exchange.sendResponseHeaders(200, if (head) -1 else sampleBytes.size.toLong())
            exchange.responseBody.use { if (!head) it.write(sampleBytes) }
        }
    }
    server.start()
    try {
        return block("http://127.0.0.1:${server.address.port}/sample.pdf", requests)
    } finally {
        server.stop(0)
    }
}

/** An in-memory [PdfRandomAccess] counting the bytes read. */
private class CountingRandomAccess(private val bytes: ByteArray) : PdfRandomAccess {
    var bytesRead = 0
    override suspend fun length() = bytes.size.toLong()
    override suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int) {
        bytes.copyInto(destination, offset, position.toInt(), position.toInt() + length)
        synchronized(this) { bytesRead += length }
    }
}

val PdfDataTest by testSuite {
    test("bytes") {
        shouldOpenSample(PdfData.bytes(sampleBytes))
    }

    test("file") {
        shouldOpenSample(PdfData.file(Path(sampleFile().absolutePath)))
    }

    test("file URI") {
        shouldOpenSample(PdfData.uri(sampleFile().toURI().toString()))
    }

    test("jar URI, e.g. a Compose resource in a desktop app") {
        val jar = File.createTempFile("resources", ".jar").apply { deleteOnExit() }
        JarOutputStream(jar.outputStream()).use {
            it.putNextEntry(JarEntry("files/sample.pdf"))
            it.write(sampleBytes)
        }
        shouldOpenSample(PdfData.uri("jar:${jar.toURI()}!/files/sample.pdf"))
    }

    test("file stream, read in place") {
        shouldOpenSample(PdfData.fromStream(FileInputStream(sampleFile())))
    }

    test("other stream, from a temporary copy") {
        shouldOpenSample(PdfData.fromStream(ByteArrayInputStream(sampleBytes)))
    }

    test("random access") {
        val source = CountingRandomAccess(sampleBytes)
        shouldOpenSample(PdfData.randomAccess(source))
        source.bytesRead shouldBeLessThanOrEqual sampleBytes.size
    }

    test("URL with range requests") {
        withServer(ranges = true) { url, requests ->
            kotlinx.coroutines.runBlocking { shouldOpenSample(PdfData.url(url)) }
            // Ranges only: the document is never downloaded in one request.
            requests.filter { it.startsWith("GET") }.forEach { it shouldNotBe "GET " }
        }
    }

    test("URL without range support falls back to a full download") {
        withServer(ranges = false) { url, requests ->
            kotlinx.coroutines.runBlocking { shouldOpenSample(PdfData.url(url)) }
            // HEAD, then the range probe, answered with the whole document.
            requests.count { it.startsWith("GET") } shouldBe 1
        }
    }

    test("closing while a progressive open waits for a read") {
        InitLib.init()
        val events = mutableListOf<String>()
        val reading = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val source = object : PdfRandomAccess {
            override suspend fun length() = sampleBytes.size.toLong()
            override suspend fun read(position: Long, destination: ByteArray, offset: Int, length: Int) {
                reading.complete(Unit)
                // Like a blocking read, it finishes even if the open is cancelled.
                withContext(NonCancellable) { gate.await() }
                sampleBytes.copyInto(destination, offset, position.toInt(), position.toInt() + length)
                synchronized(events) { events += "read" }
            }
            override fun close() {
                synchronized(events) { events += "close" }
                closed.complete(Unit)
            }
        }
        val renderer = PdfData.randomAccess(source).createRenderer(noPlatformContext!!)
        // Real time: the test scope's virtual time would fire the timeout at once.
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                // Like PdfState when the PdfData changes: the open is cancelled and the renderer closed mid-read.
                val open = async { renderer.openDocument() }
                reading.await()
                open.cancel()
                renderer.close()
                // Let the cancelled call return, so the JVM side disposes the native renderer before the read ends.
                delay(300)
                gate.complete(Unit)
                closed.await()
            }
            // Give a use-after-free in the native loop time to crash the JVM.
            delay(500)
        }
        // The source closes only once the open stopped reading.
        synchronized(events) { events.last() shouldBe "close" }
    }

    test("file URI paths") {
        fileUriPath("file:/tmp/a.pdf") shouldBe "/tmp/a.pdf"
        fileUriPath("file:///tmp/a.pdf") shouldBe "/tmp/a.pdf"
        fileUriPath("file://localhost/tmp/a.pdf") shouldBe "/tmp/a.pdf"
        fileUriPath("file:///tmp/my%20doc%C3%A9.pdf") shouldBe "/tmp/my docé.pdf"
    }
}

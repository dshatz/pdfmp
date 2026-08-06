package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.Pdfium
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi

@OptIn(ExperimentalWasmJsInterop::class, UnsafeWasmMemoryApi::class)
actual class PdfRenderer: PdfiumRenderer {
    private val source: PdfSource?
    private val customSource: CustomPdfSourceAdapter?

    actual constructor(source: CustomPdfSourceAdapter) {
        this.customSource = source
        this.source = null
    }

    actual constructor(source: PdfSource) {
        this.source = source
        this.customSource = null
    }

    init {
        Pdfium.InitLibrary()
    }
    private var sourceBytesPtr: UInt? = null

    @JniCall
    actual suspend fun openDocument(): Result<Unit> {
        return runCatching {
            if (source != null) {
                when (source) {
                    is PdfSource.PdfBytes -> {
                        val dataPtr = DataPointer(PdfiumWasm._malloc(source.bytes.size))
                        println("ptr! $dataPtr")
                        for (offset in source.bytes.indices) {
                            writeWasmByte(dataPtr.ptr + offset.toUInt(), source.bytes[offset])
                        }
                        val doc = Pdfium.LoadMemDocument(dataPtr, source.bytes.size, null)!!
                        val pageCount = Pdfium.GetPageCount(doc)
                        this.doc = doc
                        this.sourceBytesPtr = dataPtr.ptr
                        d ("Page count $pageCount!")
                    }
                    is PdfSource.PdfPath -> TODO()
                }
            }
        }
    }


    actual override fun close() {
        Pdfium.CloseDocument(doc)
        sourceBytesPtr?.let(PdfiumWasm::_free)
    }
}
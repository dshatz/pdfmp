@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package com.dshatz.pdfmp.pdfium

import com.dshatz.internal.pdfium.FPDFAvail_Create
import com.dshatz.internal.pdfium.FPDFAvail_Destroy
import com.dshatz.internal.pdfium.FPDFAvail_GetDocument
import com.dshatz.internal.pdfium.FPDFAvail_IsDocAvail
import com.dshatz.internal.pdfium.FPDFAvail_IsPageAvail
import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import com.dshatz.internal.pdfium.FX_DOWNLOADHINTS
import com.dshatz.internal.pdfium.FX_FILEAVAIL
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.posix.memcpy

actual class ProgressiveDocument actual constructor(length: Int, chunkSize: Int) {
    actual val chunks = ProgressiveChunks(length, chunkSize)

    // Its address identifies the document in the callbacks.
    private val data: CPointer<ByteVar> = nativeHeap.allocArray(length)

    // FX_FILEAVAIL followed by [data] for the callback, which only receives the struct.
    private val fileAvail: CPointer<ByteVar> =
        nativeHeap.allocArray<ByteVar>(sizeOf<FX_FILEAVAIL>() + sizeOf<COpaquePointerVar>()).also {
            val struct = it.reinterpret<FX_FILEAVAIL>().pointed
            struct.version = 1
            struct.IsDataAvail = staticCFunction { fileAvail, offset, size ->
                if (ProgressiveCallbacks.isDataAvail(fileAvail!!.dataKey(), offset.convert(), size.convert())) 1 else 0
            }
            it.dataSlot().pointed.value = data
        }

    private val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS> {
        m_FileLen = length.convert()
        m_Param = data
        m_GetBlock = staticCFunction { data, position, buffer, size ->
            val read = ProgressiveCallbacks.getBlock(data.key(), position.convert(), size.convert()) {
                memcpy(buffer, data!!.reinterpret<ByteVar>() + position.convert<Long>(), size.convert())
            }
            if (read) 1 else 0
        }
    }

    private val avail = FPDFAvail_Create(fileAvail.reinterpret(), fileAccess.ptr)

    init {
        ProgressiveCallbacks.register(data.key(), chunks)
    }

    /** Where the bytes starting at chunk [firstChunk] go; mark them received in [chunks] once written. */
    fun chunkAddress(firstChunk: Int): CPointer<ByteVar> = (data + firstChunk.toLong() * chunks.chunkSize)!!

    actual fun isDocAvail(): Availability = chunks.check { FPDFAvail_IsDocAvail(avail, downloadHints) }

    actual fun isPageAvail(pageIndex: Int): Availability =
        chunks.check { FPDFAvail_IsPageAvail(avail, pageIndex, downloadHints) }

    actual fun document(): DocHandle? = FPDFAvail_GetDocument(avail, null)?.let(::DocHandle)

    actual fun destroy() {
        FPDFAvail_Destroy(avail)
        ProgressiveCallbacks.unregister(data.key())
        nativeHeap.free(fileAccess.rawPtr)
        nativeHeap.free(fileAvail.rawValue)
        nativeHeap.free(data.rawValue)
    }
}

private fun COpaquePointer?.key(): Long = this!!.rawValue.toLong()

private fun CPointer<ByteVar>.dataSlot(): CPointer<COpaquePointerVar> =
    (this + sizeOf<FX_FILEAVAIL>())!!.reinterpret()

private fun CPointer<FX_FILEAVAIL>.dataKey(): Long = reinterpret<ByteVar>().dataSlot().pointed.value.key()

private val downloadHints: CPointer<FX_DOWNLOADHINTS> = nativeHeap.alloc<FX_DOWNLOADHINTS> {
    version = 1
    AddSegment = staticCFunction { _, offset, size ->
        ProgressiveCallbacks.addSegment(offset.convert(), size.convert())
    }
}.ptr

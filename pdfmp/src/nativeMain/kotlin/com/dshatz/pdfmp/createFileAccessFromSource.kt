package com.dshatz.pdfmp

import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.kni.buffers.DelicateBufferAPI
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.CustomSourceDescriptorNative
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import platform.posix.memset

@OptIn(UnsafeNumber::class, DelicateBufferAPI::class)
fun createFileAccessFromSource(
    sourceObj: CustomPdfSourceAdapter
): Result<CustomSourceDescriptorNative> {
    return runCatching {
        val contextHandle = StableRef.Companion.create(sourceObj)

        // Allocate the struct on the heap so it survives the function return
        val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS>()
        memset(fileAccess.ptr, 0, FPDF_FILEACCESS.Companion.size.convert())

        fileAccess.m_Param = contextHandle.asCPointer()
        fileAccess.m_FileLen = sourceObj.getDocumentLength().convert()
        fileAccess.m_GetBlock = staticCFunction { p, pos, buf, sz ->
            p!!.asStableRef<CustomPdfSourceAdapter>().get().readBlock(
                pos.convert(),
                ByteBuffer.Companion.wrapAddress(
                    buf!!.reinterpret(),
                    sz.convert(),
                    owner = buf,
                    finalizer = {})
            )
        }

        CustomSourceDescriptorNative(
            fileAccess.ptr, contextHandle
        )
    }
}
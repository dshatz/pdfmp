package com.dshatz.pdfmp.progressive

import com.dshatz.internal.pdfium.FPDF_BITMAP
import com.dshatz.internal.pdfium.FPDF_PAGE
import com.dshatz.internal.pdfium.FPDF_RENDER_FAILED
import com.dshatz.internal.pdfium.FPDF_RENDER_TOBECONTINUED
import com.dshatz.internal.pdfium.FPDF_RenderPageBitmap_Start
import com.dshatz.internal.pdfium.FPDF_RenderPage_Close
import com.dshatz.internal.pdfium.FPDF_RenderPage_Continue
import com.dshatz.internal.pdfium.IFSDK_PAUSE
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

suspend fun renderPageProgressively(
    bitmap: FPDF_BITMAP,
    page: FPDF_PAGE,
    startX: Int,
    startY: Int,
    sizeX: Int,
    sizeY: Int,
    rotate: Int = 0,
    flags: Int = 0
) = withContext(Dispatchers.Default) {
    val pause = nativeHeap.alloc<IFSDK_PAUSE>().apply {
        version = 1
        user = null
        // staticCFunction for Kotlin/Native; for JNI, this would be a Native callback
        NeedToPauseNow = staticCFunction { _ -> 
            // 1 = Pause immediately to let other coroutines/network work
            // 0 = Keep rendering if data is ready
            1 
        }
    }

    try {
        var status = FPDF_RenderPageBitmap_Start(
            bitmap = bitmap,
            page = page,
            start_x = startX,
            start_y = startY,
            size_x = sizeX,
            size_y = sizeY,
            rotate = rotate,
            flags = flags,
            pause = pause.ptr
        )

        while (status == FPDF_RENDER_TOBECONTINUED) {
            yield() 

            if (!coroutineContext.isActive) break

            status = FPDF_RenderPage_Continue(page, pause.ptr)
        }
        
        if (status == FPDF_RENDER_FAILED) {
            error("PDFium rendering failed")
        }
    } finally {
        FPDF_RenderPage_Close(page)
        nativeHeap.free(pause)
    }
}
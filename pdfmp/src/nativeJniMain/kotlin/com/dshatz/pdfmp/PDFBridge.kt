@file:OptIn(ExperimentalForeignApi::class)

package com.dshatz.pdfmp

import com.dshatz.pdfmp.PDFBridgeConst.CLASS_NAME
import com.dshatz.pdfmp.PDFBridgeConst.PACKAGE_NAME
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.PdfSource
import dev.datlag.nkommons.JNIConnect
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.io.Buffer
import kotlinx.io.readByteArray


private fun <T> returnResult(
    result: Result<T>,
    packData: Buffer.(T) -> Unit,
): ByteArray  {
    val buffer = Buffer()
    result.pack(buffer, packData)
    return buffer.readByteArray()
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "initNative"
)
fun initNative() {
    PdfRenderer.init()
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "getPageCount"
)
fun getPageCount(renderer: PdfRendererPtr): ByteArray {
    return returnResult(renderer.getRenderer().getPageCount(), Buffer::writeInt)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "createNativeRenderer"
)
fun createNativeRenderer(packedSource: ByteArray): ByteArray {
    val initResult = PdfRendererFactory.createFromSource(PdfSource.unpack(packedSource)).mapCatching { renderer ->
        val stableRef = StableRef.create(renderer)
        stableRef.asCPointer().toLong()
    }
    return returnResult(initResult, Buffer::writeLong)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "getAspectRatio"
)
fun getAspectRatio(rendererPtr: PdfRendererPtr, pageIndex: Int): Float {
    return rendererPtr.getRenderer().getAspectRatio(pageIndex)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "getPageRatios"
)
fun getPageRatios(rendererPtr: PdfRendererPtr): ByteArray {
    return returnResult(rendererPtr.getRenderer().getPageRatios(), ::packMap)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "render"
)
fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray {
    val renderer = renderer.getRenderer()
    val req = RenderRequest.unpack(reqBytes)
    return returnResult(
        renderer.render(req),
        RenderResponse::pack
    )
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "close"
)
fun close(renderer: PdfRendererPtr) {
    renderer.getRenderer().close()
}

fun PdfRendererPtr.getRenderer(): PdfRenderer {
    return runCatching {
        val rendererRef = toCPointer<COpaque>()!!.asStableRef<PdfRenderer>()
        rendererRef.get()
    }.getOrElse {
        e("Could not get renderer", it)
        error("")
    }
}



object PDFBridgeConst {
    const val CLASS_NAME = "PDFBridge"
    const val PACKAGE_NAME = "com.dshatz.pdfmp"
}
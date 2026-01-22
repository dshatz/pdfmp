@file:OptIn(ExperimentalForeignApi::class)

package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JNIConnect
import com.dshatz.pdfmp.PDFBridgeConst.CLASS_NAME
import com.dshatz.pdfmp.PDFBridgeConst.PACKAGE_NAME
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeFloat


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
    val initResult = runBlocking {
        PdfRendererFactory.createFromSource(PdfSource.unpack(packedSource)).mapCatching { renderer ->
            val stableRef = StableRef.create(renderer)
            stableRef.asCPointer().toLong()
        }
    }
    return returnResult(initResult, Buffer::writeLong)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "createNativeRendererCustom"
)
fun createNativeRendererCustom(source: CustomPdfSourceAdapter): ByteArray {
    val descriptor = createFileAccessFromSource(source)
    val initResult = descriptor.mapCatching {
        val rendererResult = PdfRendererFactory.createFromSource(PdfSource.Custom(it))
        rendererResult.mapCatching { renderer ->
            val stableRef = StableRef.create(renderer)
            stableRef.asCPointer().toLong()
        }.getOrThrow()
    }
    return returnResult(initResult, Buffer::writeLong)
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "getAspectRatio"
)
fun getAspectRatio(rendererPtr: PdfRendererPtr, pageIndex: Int): ByteArray {
    return returnResult(rendererPtr.getRenderer().getPageRatio(pageIndex), { writeFloat(it) })
}

/*@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "render"
)
fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray = runBlocking {
    val renderer = renderer.getRenderer()
    val req = RenderRequest.unpack(reqBytes)
    returnResult(
        renderer.render(req),
        RenderResponse::pack
    )
}*/

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "renderAsync"
)
fun renderAsync(renderer: PdfRendererPtr, reqBytes: ByteArray, callback: RenderCallback) {
    val renderer = renderer.getRenderer()
    val req = RenderRequest.unpack(reqBytes)
    renderer.renderAsync(req, callback)
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
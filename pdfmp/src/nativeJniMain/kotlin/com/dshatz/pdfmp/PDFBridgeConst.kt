@file:OptIn(ExperimentalForeignApi::class)

package com.dshatz.pdfmp

import com.dshatz.pdfmp.PDFBridgeConst.CLASS_NAME
import com.dshatz.pdfmp.PDFBridgeConst.PACKAGE_NAME
import com.dshatz.pdfmp.source.PdfSource
import dev.datlag.nkommons.JNIConnect
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong


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
fun getPageCount(renderer: PdfRendererPtr): Int {
    return renderer.getRenderer().getPageCount()
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "openFile"
)
fun openFile(packedSource: ByteArray): PdfRendererPtr {
    val renderer = PdfRenderer(PdfSource.unpack(packedSource))
    renderer.openFile()
    val stableRef = StableRef.create(renderer)
    return stableRef.asCPointer().toLong()
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
    return rendererPtr.getRenderer().getPageRatios().pack()
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "render"
)
fun render(renderer: PdfRendererPtr, reqBytes: ByteArray): ByteArray {
    val renderer = renderer.getRenderer()
    val req = RenderRequest.unpack(reqBytes)
    return renderer.render(req).pack()
}

@JNIConnect(
    packageName = PACKAGE_NAME,
    className = CLASS_NAME,
    functionName = "close"
)
fun close(renderer: PdfRendererPtr) {
    renderer.getRenderer().close()
}

/*@OptIn(ExperimentalNativeApi::class)
@CName("Java_com_dshatz_pdfmp_${CLASS_NAME}_getBufferAddress")
public fun _getBufferAddress(
    env: CPointer<JNIEnvVar>,
    clazz: jobject,
    buffer: jobject,
): jlong {
    return buffer.toLong()
}*/

fun PdfRendererPtr.getRenderer(): PdfRenderer {
    return runCatching {
        val rendererRef = toCPointer<COpaque>()!!.asStableRef<PdfRenderer>()
        rendererRef.get()
    }.getOrElse {
        println("Could not get renderer: ${it.message}")
        error("")
    }
}



object PDFBridgeConst {
    const val CLASS_NAME = "PDFBridge"
    const val PACKAGE_NAME = "com.dshatz.pdfmp"
}
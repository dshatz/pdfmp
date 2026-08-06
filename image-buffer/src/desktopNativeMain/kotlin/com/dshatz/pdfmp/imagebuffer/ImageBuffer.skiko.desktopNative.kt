package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.JNIEnvVar
import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.wrapper.NativeJniAdapter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCPointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.Managed
import org.jetbrains.skia.impl.NativePointer

@JniAdapter(adapter = BitmapAdapter::class)
actual data class ImageBuffer(
    val pixmap: Pixmap
): IImageBuffer, WritableImageBuffer {
    actual override val width: Int = pixmap.info.width
    actual override val height: Int = pixmap.info.height
    actual override val stride: Int = pixmap.rowBytes

    override fun <R> withWritableAddress(block: (address: CPointer<*>) -> R) {
        block(pixmap.addr.toLong().toCPointer<ByteVar>()!!)
    }
}

@OptIn(ExperimentalForeignApi::class)
object BitmapAdapter :
    NativeJniAdapter<ImageBuffer, SkBitmapInfo> {
    override fun fromJni(
        env: CPointer<JNIEnvVar>,
        value: SkBitmapInfo
    ): ImageBuffer {
        val ptr: COpaquePointer = value.ptr.toCPointer()!!
        val data = Data.makeWithoutCopy(
            ptr.rawValue,
            length = value.strideBytes * value.height,
            object: Managed(ptr.rawValue, NativePointer.NULL, false) {}
        )
        val imageInfo = ImageInfo(
            width = value.width,
            height = value.height,
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.PREMUL
        )
        val pixmap = Pixmap.make(imageInfo, data, value.strideBytes)
        return ImageBuffer(pixmap)
    }

    override fun toJni(
        env: CPointer<JNIEnvVar>,
        value: ImageBuffer
    ): SkBitmapInfo {
        return SkBitmapInfo(
            value.pixmap.addr.toLong(),
            value.width,
            value.height,
            value.stride
        )
    }

}
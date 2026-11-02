package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.JNIEnvVar
import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.wrapper.NativeJniAdapter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong

/** Raw pixel memory. [owned] means we allocated it; JVM-provided memory is freed by the JVM. */
@OptIn(ExperimentalForeignApi::class)
@JniAdapter(adapter = ImageBufferAdapter::class)
actual data class ImageBuffer(
    val pixels: CPointer<ByteVar>,
    actual override val width: Int,
    actual override val height: Int,
    actual override val stride: Int,
    val owned: Boolean = false,
): IImageBuffer, WritableImageBuffer {
    private var closed = false

    actual override fun close() {
        if (owned && !closed) {
            closed = true
            nativeHeap.free(pixels.rawValue)
        }
    }

    actual override fun reset() {

    }

    override fun <R> withWritableAddress(block: (address: CPointer<*>) -> R) {
        block(pixels)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual object ImageBufferAdapter :
    NativeJniAdapter<ImageBuffer, SkBitmapInfo> {
    override fun fromJni(
        env: CPointer<JNIEnvVar>,
        value: SkBitmapInfo
    ): ImageBuffer {
        return ImageBuffer(
            value.ptr.toCPointer()!!,
            value.width,
            value.height,
            value.strideBytes
        )
    }

    override fun toJni(
        env: CPointer<JNIEnvVar>,
        value: ImageBuffer
    ): SkBitmapInfo {
        return SkBitmapInfo(
            value.pixels.toLong(),
            value.width,
            value.height,
            value.stride
        )
    }
}

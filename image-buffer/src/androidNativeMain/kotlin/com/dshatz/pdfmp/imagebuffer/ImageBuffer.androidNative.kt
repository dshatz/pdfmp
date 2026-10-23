package com.dshatz.pdfmp.imagebuffer

import com.dshatz.kni.JNIEnvVar
import com.dshatz.kni.JavaVMVar
import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.binding.jobject
import com.dshatz.kni.utils.NewGlobalRef
import com.dshatz.kni.utils.DeleteGlobalRef
import com.dshatz.kni.utils.WithAttachedThread
import com.dshatz.kni.utils.getJavaVM
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.android.AndroidBitmapInfo
import platform.android.AndroidBitmap_getInfo
import platform.android.AndroidBitmap_lockPixels
import platform.android.AndroidBitmap_unlockPixels

@OptIn(ExperimentalForeignApi::class)
@JniAdapter(adapter = ImageBufferAdapter::class)
actual data class ImageBuffer(
    val bitmap: jobject,
    actual override val width: Int,
    actual override val height: Int,
    actual override val stride: Int,
    val jvm: CPointer<JavaVMVar>
): IImageBuffer, WritableImageBuffer, AutoCloseable {
    fun <R> useLocked(block: (address: CPointer<*>) -> R) {
        return jvm.WithAttachedThread { env ->
            memScoped {
                val pixelsPtrVar = alloc<COpaquePointerVar>()
                AndroidBitmap_lockPixels(env, bitmap, pixelsPtrVar.ptr)
                try {
                    block(pixelsPtrVar.value!!)
                } finally {
                    AndroidBitmap_unlockPixels(env, bitmap)
                }
            }
        }
    }

    override fun <R> withWritableAddress(block: (address: CPointer<*>) -> R) {
        return useLocked(block)
    }

    actual override fun close() {
        jvm.WithAttachedThread { env ->
            // android (jvm) will release the bitmap itself.
            println("Deleting Global Ref for ImageBuffer")
            env.DeleteGlobalRef(bitmap)
        }
    }

    actual override fun reset() {

    }
}

@OptIn(ExperimentalForeignApi::class)
actual object ImageBufferAdapter :
    com.dshatz.kni.wrapper.NativeJniAdapter<ImageBuffer, jobject> {
    override fun fromJni(
        env: CPointer<JNIEnvVar>,
        value: jobject
    ): ImageBuffer {
        return memScoped {
            val bitmapInfo = alloc<AndroidBitmapInfo>()
            AndroidBitmap_getInfo(env, value, bitmapInfo.ptr)
            ImageBuffer(
                env.NewGlobalRef(value),
                bitmapInfo.width.convert(),
                bitmapInfo.height.convert(),
                bitmapInfo.stride.convert<Int>(),
                env.getJavaVM()
            )
        }
    }

    override fun toJni(
        env: CPointer<JNIEnvVar>,
        value: ImageBuffer
    ): jobject {
        return value.bitmap
    }
}
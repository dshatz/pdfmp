package com.dshatz.pdfmp

import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.JavaVMVar
import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jmethodID
import dev.datlag.nkommons.binding.jobject
import kotlinx.cinterop.*

expect fun CPointer<JNIEnvVar>.NewDirectByteBuffer(
    pBuf: CPointer<UByteVar>?,
    size: ULong
): jobject?

expect fun CPointer<JNIEnvVar>.CallIntMethod(
    jniSourceContext: JniSourceContext,
    position: Long,
    directBuffer: jobject
): Int

expect fun CPointer<JNIEnvVar>.DeleteLocalRef(
    jobject: jobject
)

data class JniSourceContext(
    val jvm: CPointer<JavaVMVar>,
    val sourceObj: jobject,
    val readMethodId: jmethodID
)

expect val JNI_VERSION_1_6: jint
expect val JNI_OK: jint

fun getBlockCallback(
    param: COpaquePointer?,
    position: UInt,
    pBuf: CPointer<UByteVar>?,
    size: UInt
): Int {
    if (param == null || pBuf == null) return 0

    val contextRef = param.asStableRef<JniSourceContext>()
    val context = contextRef.get()

    return memScoped {
        val envStorage = alloc<CPointerVar<JNIEnvVar>>()

        val getEnvRes = context.jvm.pointed.pointed!!.GetEnv!!.invoke(
            context.jvm,
            envStorage.ptr.reinterpret(),
            JNI_VERSION_1_6
        )

        if (getEnvRes != JNI_OK) return@memScoped 0

        val env = envStorage.value!!

        val directBuffer = env.NewDirectByteBuffer(
            pBuf.reinterpret(),
            size.toULong()
        )

        if (directBuffer == null) return@memScoped 0

        // Call jvm
        val bytesRead = env.CallIntMethod(
            context,
            position.toLong(),
            directBuffer
        )

        env.DeleteLocalRef(directBuffer)

        // Return 1 if we read exactly the requested amount
        if (bytesRead == size.toInt()) 1 else 0
    }
}
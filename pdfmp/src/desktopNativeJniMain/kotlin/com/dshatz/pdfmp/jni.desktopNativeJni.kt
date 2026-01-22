package com.dshatz.pdfmp

import dev.datlag.nkommons.binding.jclass
import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jobject
import jni.JNIEnvVar
import jni.JNI_OK
import jni.JNI_VERSION_1_6
import jni.jvalue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret

actual val JNI_VERSION_1_6: jint = JNI_VERSION_1_6
actual val JNI_OK: jint = JNI_OK

actual fun CPointer<JNIEnvVar>.NewDirectByteBuffer(
    pBuf: CPointer<UByteVar>?,
    size: ULong
): jobject? {
    return pointed.pointed!!.NewDirectByteBuffer!!.invoke(this, pBuf, size.toLong())
}

actual fun CPointer<dev.datlag.nkommons.JNIEnvVar>.DeleteLocalRef(jobject: jobject) {
    pointed.pointed!!.DeleteLocalRef!!.invoke(this, jobject.reinterpret())
}

actual fun CPointer<dev.datlag.nkommons.JNIEnvVar>.CallIntMethod(
    jniSourceContext: JniSourceContext,
    position: Long,
    directBuffer: jobject
): Int = memScoped {
    val args = allocArray<dev.datlag.nkommons.jvalue>(2)

    args.get(0).j = position
    args.get(1).l = directBuffer.reinterpret()

    val bytesRead = pointed.pointed!!.CallIntMethodA!!.invoke(
        this@CallIntMethod,
        jniSourceContext.sourceObj.reinterpret(),
        jniSourceContext.readMethodId.reinterpret(),
        args
    )

    return bytesRead
}
package com.dshatz.pdfmp

import cnames.structs._jmethodID
import com.dshatz.internal.pdfium.FPDF_FILEACCESS
import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.jvalue
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.android.JNI_OK
import platform.android.JNI_VERSION_1_6
import platform.android.JavaVMVar
import platform.android.jclass
import platform.android.jobject

@OptIn(UnsafeNumber::class)
actual fun MemScope.createFileAccessFromSource(
    env: CPointer<JNIEnvVar>,
    obj: dev.datlag.nkommons.binding.jobject
): Result<FPDF_FILEACCESS> {
    return runCatching {
        val sourceClass = env.pointed.pointed!!.GetObjectClass!!.invoke(env, obj)

        val readMethodId = env.pointed.pointed!!.GetMethodID!!(
            env, sourceClass, "readBlock".cstr.ptr, "(JLjava/nio/ByteBuffer;)I".cstr.ptr
        )

        if (readMethodId == null) throw RuntimeException("JNI method not found")// Method not found exception thrown by JVM

        val jvmPtr = nativeHeap.alloc<CPointerVar<JavaVMVar>>()
        env.pointed.pointed!!.GetJavaVM!!(env, jvmPtr.ptr)

        val globalSourceRef = env.pointed.pointed!!.NewGlobalRef!!(env, obj)

        val context = JniSourceContext(jvmPtr.value!!, globalSourceRef!!, readMethodId)
        val stableRef = StableRef.create(context)

        val fileAccess = nativeHeap.alloc<FPDF_FILEACCESS>()

        fileAccess.m_Param = stableRef.asCPointer()
        fileAccess.m_GetBlock = staticCFunction(::getBlockCallback)
        fileAccess
    }
}

actual val JNI_VERSION_1_6: jint = JNI_VERSION_1_6
actual val JNI_OK: jint = JNI_OK
actual fun CPointer<JNIEnvVar>.NewDirectByteBuffer(
    pBuf: CPointer<UByteVar>?,
    size: ULong
): dev.datlag.nkommons.binding.jobject? {
    return pointed.pointed!!.NewDirectByteBuffer!!.invoke(this, pBuf, size.toLong())
}

actual fun CPointer<JNIEnvVar>.CallIntMethod(
    jniSourceContext: JniSourceContext,
    position: Long,
    directBuffer: dev.datlag.nkommons.binding.jobject
): Int = memScoped {
    val args = allocArray<platform.android.jvalue>(2)

    args.get(0).j = position
    args.get(1).l = directBuffer

    val bytesRead = pointed.pointed!!.CallIntMethodA!!.invoke(
        this@CallIntMethod,
        jniSourceContext.sourceObj,
        jniSourceContext.readMethodId.reinterpret(),
        args
    )

    return bytesRead
}

actual fun CPointer<JNIEnvVar>.DeleteLocalRef(jobject: dev.datlag.nkommons.binding.jobject) {
    pointed.pointed!!.DeleteLocalRef!!.invoke(this, jobject)
}
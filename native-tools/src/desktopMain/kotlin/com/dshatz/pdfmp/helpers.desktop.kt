package com.dshatz.pdfmp

import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.binding.jclass
import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jlong
import dev.datlag.nkommons.binding.jmethodID
import dev.datlag.nkommons.binding.jobject
import dev.datlag.nkommons.jvalue
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun dev.datlag.nkommons.JNIInvokeInterface.AttachCurrentThread(
    env: kotlinx.cinterop.CPointer<dev.datlag.nkommons.JavaVMVar>,
    pointer: kotlinx.cinterop.CPointer<kotlinx.cinterop.COpaquePointerVar>
): jint {
    return AttachCurrentThread!!.invoke(
        env,
        pointer,
        null
    )
}

actual fun CPointer<JNIEnvVar>.NewDirectByteBuffer(
    pBuf: CPointer<UByteVar>?,
    size: jlong
): jobject? {
    return pointed.pointed!!.NewDirectByteBuffer!!.invoke(this, pBuf, size)
}

actual fun CPointer<JNIEnvVar>.FindClass(name: String): jclass? = memScoped {
    pointed.pointed!!.FindClass!!.invoke(this@FindClass, name.cstr.ptr)
}

actual fun CPointer<JNIEnvVar>.NewGlobalRef(obj: jobject): jobject? {
    return pointed.pointed!!.NewGlobalRef!!.invoke(this, obj.reinterpret())
}

actual fun CPointer<JNIEnvVar>.GetStaticMethodID(
    classRef: jobject,
    methodName: String,
    signature: String
): jmethodID? = memScoped {
    pointed.pointed!!.GetStaticMethodID!!.invoke(
        this@GetStaticMethodID,
        classRef.reinterpret(),
        methodName.cstr.ptr,
        signature.cstr.ptr
    )
}

actual fun CPointer<JNIEnvVar>.DeleteLocalRef(jobject: jobject) {
    pointed.pointed!!.DeleteLocalRef!!.invoke(this, jobject.reinterpret())
}

actual fun CPointer<JNIEnvVar>.CallStaticIntMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jint {
    return pointed.pointed!!.CallStaticIntMethodA!!.invoke(
        this,
        cls.reinterpret(),
        method.reinterpret(),
        args
    )
}

actual fun CPointer<JNIEnvVar>.CallStaticLongMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): ULong {
    return pointed.pointed!!.CallStaticLongMethodA!!.invoke(
        this,
        cls.reinterpret(),
        method.reinterpret(),
        args
    ).toULong()
}

actual fun CPointer<JNIEnvVar>.CallStaticObjectMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jobject? {
    return pointed.pointed!!.CallStaticObjectMethodA!!.invoke(
        this,
        cls.reinterpret(),
        method.reinterpret(),
        args
    )
}
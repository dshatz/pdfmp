package com.dshatz.pdfmp

import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.JNIInvokeInterface
import dev.datlag.nkommons.JavaVMVar
import dev.datlag.nkommons.binding.jclass
import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jlong
import dev.datlag.nkommons.binding.jmethodID
import dev.datlag.nkommons.binding.jobject
import dev.datlag.nkommons.jvalue
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret

@OptIn(ExperimentalForeignApi::class)
actual fun JNIInvokeInterface.AttachCurrentThread(
    env: CPointer<JavaVMVar>,
    pointer: CPointer<COpaquePointerVar>
): jint {
    return AttachCurrentThread!!.invoke(
        env,
        pointer.reinterpret(),
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
    return pointed.pointed!!.NewGlobalRef!!.invoke(this, obj)
}

actual fun CPointer<JNIEnvVar>.GetStaticMethodID(
    classRef: jobject,
    methodName: String,
    signature: String
): jmethodID? = memScoped {
    pointed.pointed!!.GetStaticMethodID!!.invoke(
        this@GetStaticMethodID,
        classRef,
        methodName.cstr.ptr,
        signature.cstr.ptr
    )
}

actual fun CPointer<JNIEnvVar>.DeleteLocalRef(jobject: jobject) {
    pointed.pointed!!.DeleteLocalRef!!.invoke(this, jobject)
}

actual fun CPointer<JNIEnvVar>.CallStaticIntMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jint {
    return pointed.pointed!!.CallStaticIntMethodA!!.invoke(
        this,
        cls,
        method.reinterpret(),
        args
    )
}

actual fun CPointer<JNIEnvVar>.CallStaticObjectMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jobject? {
    return pointed.pointed!!.CallStaticObjectMethodA!!.invoke(
        this,
        cls,
        method.reinterpret(),
        args
    )?.reinterpret()
}

actual fun CPointer<JNIEnvVar>.CallStaticLongMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): ULong {
    return pointed.pointed!!.CallStaticLongMethodA!!.invoke(
        this,
        cls,
        method.reinterpret(),
        args
    ).toULong()
}
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
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed

expect fun JNIInvokeInterface.AttachCurrentThread(
    env: CPointer<JavaVMVar>,
    pointer: CPointer<COpaquePointerVar>
): jint



expect fun CPointer<JNIEnvVar>.NewDirectByteBuffer(
    pBuf: CPointer<UByteVar>?,
    size: jlong
): jobject?

expect fun CPointer<JNIEnvVar>.FindClass(name: String): jclass?

expect fun CPointer<JNIEnvVar>.NewGlobalRef(obj: jobject): jobject?

expect fun CPointer<JNIEnvVar>.GetStaticMethodID(
    classRef: jobject,
    methodName: String,
    signature: String
): jmethodID?


expect fun CPointer<JNIEnvVar>.DeleteLocalRef(
    jobject: jobject
)

expect fun CPointer<JNIEnvVar>.CallStaticIntMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jint

expect fun CPointer<JNIEnvVar>.CallStaticObjectMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): jobject?

expect fun CPointer<JNIEnvVar>.CallStaticLongMethodA(
    cls: jclass,
    method: jmethodID,
    args: CPointer<jvalue>
): ULong



fun CPointer<JNIEnvVar>.CheckException(): Boolean {
    if (pointed.pointed!!.ExceptionCheck!!(this) == 1.toUByte()) {
        pointed.pointed!!.ExceptionDescribe!!(this)
        pointed.pointed!!.ExceptionClear!!(this)
        return true
    } else return false
}
package com.dshatz.pdfmp

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toLong
import kotlinx.cinterop.value
import platform.android.AndroidBitmapInfo
import platform.android.AndroidBitmap_getInfo
import platform.android.AndroidBitmap_lockPixels
import platform.android.AndroidBitmap_unlockPixels
import platform.android.JNIEnvVar
import platform.android.jobject
import platform.android.jvalue
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("Java_com_dshatz_pdfmp_ConsumerBufferUtil_lockBitmap")
fun lockBitmap(env: CPointer<JNIEnvVar>, thiz: jobject, bitmap: jobject): jobject? {
    memScoped {
        val jni = env.pointed.pointed ?: return null

        val pixelsPtrVar = alloc<COpaquePointerVar>()
        val ret = AndroidBitmap_lockPixels(env, bitmap, pixelsPtrVar.ptr)
        if (ret < 0) return null
        val pixelsAddress = pixelsPtrVar.value?.toLong() ?: 0L

        val info = alloc<AndroidBitmapInfo>()
        AndroidBitmap_getInfo(env, bitmap, info.ptr)

        // Construct 'BufferDimensions' object
        // Class Path: com/dshatz/pdfmp/model/BufferDimensions
        // Signature: (III)V -> width, height, stride
        val dimsCls = jni.FindClass!!(env, "com/dshatz/pdfmp/model/BufferDimensions".cstr.ptr)
        val dimsCtor = jni.GetMethodID!!(env, dimsCls, "<init>".cstr.ptr, "(III)V".cstr.ptr)

        val dimsArgs = allocArray<jvalue>(3)
        dimsArgs[0].i = info.width.toInt()
        dimsArgs[1].i = info.height.toInt()
        dimsArgs[2].i = info.stride.toInt()

        val dimsObj = jni.NewObjectA!!(env, dimsCls, dimsCtor, dimsArgs)


        // Construct 'BufferInfo' object
        // Class Path: com/dshatz/pdfmp/model/BufferInfo
        // Signature: (Lcom/dshatz/pdfmp/model/BufferDimensions;J)V -> dimensionsObj, address
        val infoCls = jni.FindClass!!(env, "com/dshatz/pdfmp/model/BufferInfo".cstr.ptr)
        val infoCtor = jni.GetMethodID!!(env, infoCls, "<init>".cstr.ptr, "(Lcom/dshatz/pdfmp/model/BufferDimensions;J)V".cstr.ptr)

        val infoArgs = allocArray<jvalue>(2)
        infoArgs[0].l = dimsObj
        infoArgs[1].j = pixelsAddress

        return jni.NewObjectA!!(env, infoCls, infoCtor, infoArgs)
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("Java_com_dshatz_pdfmp_ConsumerBufferUtil_unlockBitmap")
fun unlockBitmap(env: CPointer<JNIEnvVar>, thiz: jobject, bitmap: jobject) {
    AndroidBitmap_unlockPixels(env, bitmap)
}
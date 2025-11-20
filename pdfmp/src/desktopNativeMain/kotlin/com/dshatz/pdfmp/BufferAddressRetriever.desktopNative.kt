package com.dshatz.pdfmp

import jni.jlong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName(externName = "Java_com_dshatz_pdfmp_PDFBridge_getBufferAddress")
fun _getBufferAddress(
    env: kotlinx.cinterop.CPointer<dev.datlag.nkommons.JNIEnvVar>,
    clazz: dev.datlag.nkommons.binding.jobject,
    buffer: dev.datlag.nkommons.binding.jobject
): dev.datlag.nkommons.binding.jlong {
    val addressPointer = env.pointed.pointed?.GetDirectBufferAddress?.invoke(env, buffer.reinterpret())
    val address = addressPointer?.rawValue?.toLong()?.convert<jlong>()
    return address ?: run {
        println("Address is null!")
        -1
    }
}
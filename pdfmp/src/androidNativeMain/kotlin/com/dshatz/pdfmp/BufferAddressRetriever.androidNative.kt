package com.dshatz.pdfmp

import dev.datlag.nkommons.JNIEnvVar
import dev.datlag.nkommons.binding.jlong
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.pointed
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName(externName = "Java_com_dshatz_pdfmp_PDFBridge_getBufferAddress")
fun _getBufferAddress(
    env: CPointer<JNIEnvVar>,
    clazz: dev.datlag.nkommons.binding.jobject,
    buffer: dev.datlag.nkommons.binding.jobject
): jlong {
    val addressPointer = env.pointed.pointed?.GetDirectBufferAddress?.invoke(env, buffer)
    val address = addressPointer?.rawValue?.toLong()?.convert<jlong>()
    return address ?: run {
        println("Address is null!")
        -1
    }
}
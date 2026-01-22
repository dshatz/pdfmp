package com.dshatz.pdfmp

import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jobject
import dev.datlag.nkommons.jvalue
import kotlinx.cinterop.reinterpret

actual val JNI_VERSION_1_6: jint = jni.JNI_VERSION_1_6
actual val JNI_OK: jint = jni.JNI_OK
actual val JNI_EDETACHED: jint = jni.JNI_EDETACHED
actual var jvalue.l: jobject?
    get() = this.l
    set(value) {
        this.l = value?.reinterpret()
    }
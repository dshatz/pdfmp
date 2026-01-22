package com.dshatz.pdfmp

import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jobject
import dev.datlag.nkommons.jvalue

actual val JNI_VERSION_1_6: jint = platform.android.JNI_VERSION_1_6
actual val JNI_OK: jint = platform.android.JNI_OK
actual val JNI_EDETACHED: jint = platform.android.JNI_EDETACHED

actual var jvalue.l: jobject?
    get() = this.l
    set(value) {
        this.l = value
    }
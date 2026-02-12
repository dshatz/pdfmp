package com.dshatz.pdfmp

import dev.datlag.nkommons.binding.jint
import dev.datlag.nkommons.binding.jobject
import dev.datlag.nkommons.jvalue

expect val JNI_VERSION_1_6: jint
expect val JNI_OK: jint
expect val JNI_EDETACHED: jint


expect var jvalue.l: jobject?
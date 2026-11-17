package com.dshatz.pdfmp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

// JNI libraries have their own PDFMP copy, so the env var is how desktop enables native logs.
@OptIn(ExperimentalForeignApi::class)
actual fun isDebug(): Boolean = PDFMP.enableLogs || getenv("PDFMP_DEBUG")?.toKString() == "true"

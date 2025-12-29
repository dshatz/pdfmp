package com.dshatz.pdfmp

import platform.Foundation.NSLog

internal actual fun logPlatform(level: com.dshatz.pdfmp.LogLevel, tag: String, message: String) {
    NSLog("[$tag] $message")
}
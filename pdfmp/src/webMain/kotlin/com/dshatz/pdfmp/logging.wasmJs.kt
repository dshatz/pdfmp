package com.dshatz.pdfmp

actual fun isDebug(): Boolean {
    return false
}

internal actual fun logPlatform(
    level: LogLevel,
    tag: String,
    message: String
) {
    logUsingPrintln(level, tag, message)
}
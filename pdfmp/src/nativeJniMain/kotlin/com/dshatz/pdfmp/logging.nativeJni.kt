package com.dshatz.pdfmp

internal actual fun logPlatform(level: LogLevel, tag: String, message: String) {
    logUsingPrintln(level, tag, message)
}
package com.dshatz.pdfmp

actual fun isDebug(): Boolean = PDFMP.enableLogs

internal actual fun logPlatform(
    level: LogLevel,
    tag: String,
    message: String
) {
    logUsingPrintln(level, tag, message)
}

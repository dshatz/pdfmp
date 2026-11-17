package com.dshatz.pdfmp

actual fun isDebug(): Boolean {
    return PDFMP.enableLogs || System.getenv("PDFMP_DEBUG") == "true"
}

internal actual fun logPlatform(level: com.dshatz.pdfmp.LogLevel, tag: String, message: String) {
    logUsingPrintln(level, tag, message)
}

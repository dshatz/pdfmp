package com.dshatz.pdfmp

class PDFFMPNativeException(
    message: String,
    val nativeStackTrace: String
): RuntimeException(message)
package com.dshatz.pdfmp

import kotlinx.io.files.Path

expect fun checkFilePath(path: Path): Boolean

expect fun readResource(name: String): ByteArray
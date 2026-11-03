package com.dshatz.pdfmp

import kotlinx.io.files.Path

// The browser has no file system.
actual fun checkFilePath(path: Path): Boolean = false

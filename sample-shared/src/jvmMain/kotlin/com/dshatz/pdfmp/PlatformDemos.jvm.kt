package com.dshatz.pdfmp

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path

internal actual fun platformDemos(): List<Demo> = emptyList()

internal actual fun PlatformFile.toPdfData(): PdfData = PdfData.file(Path(file.absolutePath))

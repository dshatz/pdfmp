package com.dshatz.pdfmp

import io.github.vinceglb.filekit.PlatformFile

internal actual fun platformDemos(): List<Demo> = emptyList()

internal actual fun PlatformFile.toPdfData(): PdfData = PdfData.uri(nsUrl.absoluteString ?: error("No URL for $nsUrl"))

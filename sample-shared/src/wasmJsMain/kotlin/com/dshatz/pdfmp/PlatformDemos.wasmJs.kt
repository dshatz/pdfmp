@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.WebFile
import org.w3c.files.Blob

internal actual fun platformDemos(): List<Demo> = emptyList()

internal actual fun PlatformFile.toPdfData(): PdfData = when (val picked = webFile) {
    is WebFile.FileWrapper -> PdfData.fromBlob(picked.file.unsafeCast<Blob>())
    else -> error("Not a picked file: $picked")
}

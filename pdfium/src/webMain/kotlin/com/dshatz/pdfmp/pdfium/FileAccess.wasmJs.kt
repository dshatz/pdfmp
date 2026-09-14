package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.Ptr
import kotlinx.serialization.Serializable

@Serializable
actual class FileAccess(
    val callback: Ptr
)
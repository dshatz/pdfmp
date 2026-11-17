package com.dshatz.pdfmp.pdfium

import com.dshatz.pdfmp.Ptr
import kotlinx.serialization.Serializable

@Serializable
actual class DocHandle(
    val ptr: Ptr,
    /** Form-fill environment, see [Pdfium.FFLDraw]. pdfium keeps using [formInfo] until it is closed. */
    val form: Ptr? = null,
    val formInfo: Ptr? = null,
)

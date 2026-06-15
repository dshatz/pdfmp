package com.dshatz.pdfmp.model

import com.dshatz.kni.annotations.JniSerializable
import com.dshatz.pdfmp.packList
import com.dshatz.pdfmp.unpackList
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

@JniSerializable
data class RenderResponse(
    val transforms: List<PageTransform>,
)
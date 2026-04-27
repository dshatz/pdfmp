package com.dshatz.pdfmp

import com.dshatz.kni.annotations.AddJniSerializer
import com.dshatz.kni.serialization.JniSerializer
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.BufferInfo.Companion.pack
import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.RenderRequest
import kotlinx.io.Buffer

@AddJniSerializer
object RenderRequestSerializer: JniSerializer<RenderRequest> {
    override fun packTo(value: RenderRequest, buffer: Buffer) {
        value.transforms.packList(
            buffer,
            packItem = PageTransform::pack
        )
        buffer.writeInt(value.pageSpacing)
        buffer.writeInt(value.topOffset)
        value.bufferInfo.pack(buffer)
    }

    override fun unpackFrom(buffer: Buffer): RenderRequest {
        val imageTransforms = unpackList(
            buffer,
            unpackItem = PageTransform::unpack
        )

        val pageSpacing = buffer.readInt()
        val topOffset = buffer.readInt()
        val bufferInfo = BufferInfo.unpack(buffer)
        return RenderRequest(
            imageTransforms,
            pageSpacing,
            topOffset,
            bufferInfo
        )
    }
}
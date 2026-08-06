package com.dshatz.pdfmp.imagebuffer

import android.graphics.Bitmap
import com.dshatz.kni.annotations.JniAdapter
import com.dshatz.kni.wrapper.JvmJniAdapter

@JniAdapter(AndroidBitmapAdapter::class)
actual class ImageBuffer(
    val bitmap: Bitmap,
) : IImageBuffer {
    actual override val width: Int = bitmap.width
    actual override val height: Int = bitmap.height
    actual override val stride: Int = bitmap.rowBytes
}

object AndroidBitmapAdapter: JvmJniAdapter<ImageBuffer, Bitmap> {
    override fun getJniValue(value: ImageBuffer): Bitmap {
        return value.bitmap
    }
    override fun fromJniValue(value: Bitmap): ImageBuffer {
        return ImageBuffer(value)
    }
}
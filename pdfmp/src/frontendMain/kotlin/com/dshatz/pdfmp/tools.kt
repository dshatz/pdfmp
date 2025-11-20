package com.dshatz.pdfmp

fun PdfRenderer.JvmRenderResponse.pixelArgbIntArray(): IntArray {
//    if (pixelData.isEmpty()) return IntArray(0)

    val buffer = this.byteBuffer
    /*val buffer = ByteBuffer.wrap(pixelData)
        // Use LITTLE_ENDIAN.
        // PDFium writes bytes as [Blue, Green, Red, Alpha].
        // Reading them as Little Endian converts this sequence into
        // the integer 0xAARRGGBB
        .order(ByteOrder.LITTLE_ENDIAN)*/
    // Make sure we read from the beginning.
    buffer.rewind()
    println("Converting to intArray ${buffer.capacity()}")

    val intBuffer = buffer.asIntBuffer()
    val ints = IntArray(intBuffer.remaining())
    intBuffer.get(ints)
    return ints
}
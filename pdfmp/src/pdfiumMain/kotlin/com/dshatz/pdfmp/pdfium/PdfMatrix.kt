package com.dshatz.pdfmp.pdfium

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PdfMatrix(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f
) {

    operator fun times(other: PdfMatrix): PdfMatrix {
        return PdfMatrix(
            a = a * other.a + b * other.c,
            b = a * other.b + b * other.d,
            c = c * other.a + d * other.c,
            d = c * other.b + d * other.d,
            e = e * other.a + f * other.c + other.e,
            f = e * other.b + f * other.d + other.f
        )
    }

    fun scale(scaleX: Float, scaleY: Float): PdfMatrix {
        return this * PdfMatrix(a = scaleX, d = scaleY)
    }

    fun translate(x: Float, y: Float): PdfMatrix {
        return this * PdfMatrix(e = x, f = y)
    }

    fun rotate(degrees: Float): PdfMatrix {
        val rad = (degrees * PI / 180.0).toFloat()
        val cosVal = cos(rad)
        val sinVal = sin(rad)
        return this * PdfMatrix(
            a = cosVal,
            b = sinVal,
            c = -sinVal,
            d = cosVal
        )
    }

    companion object {
        val Identity = PdfMatrix()
    }
}
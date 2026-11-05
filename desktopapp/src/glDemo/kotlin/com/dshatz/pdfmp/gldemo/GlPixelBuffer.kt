package com.dshatz.pdfmp.gldemo

import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import org.jetbrains.skia.impl.Managed
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL42
import org.lwjgl.opengl.GL44
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

/**
 * A persistently mapped GL pixel buffer plus the texture it feeds. pdfium renders into the mapped memory
 * ([imageBuffer]), then [upload] moves it to [texture] on the GPU without a CPU-side copy.
 *
 * Create, [upload] and [close] on the thread that owns the GL context (Skiko's render thread).
 * [imageBuffer] can be written from any thread.
 */
class GlPixelBuffer(val width: Int, val height: Int, readable: Boolean) {
    val stride = width * 4
    private val size = stride * height
    private val pbo: Int
    val texture: Int
    private val mapped: ByteBuffer
    val imageBuffer: ImageBuffer

    init {
        ensureCapabilities()
        // pdfium blends into the bitmap, so it reads it too. Read-write mappings usually get cached memory,
        // write-only ones write-combined memory where reads are very slow.
        val flags = GL30.GL_MAP_WRITE_BIT or GL44.GL_MAP_PERSISTENT_BIT or GL44.GL_MAP_COHERENT_BIT or
            (if (readable) GL30.GL_MAP_READ_BIT else 0)

        pbo = GL15.glGenBuffers()
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo)
        GL44.glBufferStorage(GL21.GL_PIXEL_UNPACK_BUFFER, size.toLong(), flags)
        mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_UNPACK_BUFFER, 0L, size.toLong(), flags)
            ?: error("glMapBufferRange failed, GL error ${GL11.glGetError()}")
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)

        texture = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8, width, height)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)

        val addr = MemoryUtil.memAddress(mapped)
        // Same wrapping as ImageBufferAdapter.fromJniValue: pdfium gets the pointer, nothing is copied or freed.
        val data = Data.makeWithoutCopy(addr, size, object : Managed(addr, 0, false) {})
        val pixmap = Pixmap.make(
            ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, null), width, height),
            data,
            stride,
        )
        imageBuffer = ImageBuffer(pixmap)
    }

    /** Copies the mapped pixels into [texture], on the GPU. pdfium must be done writing. */
    fun upload() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
        // pdfium writes BGRA; the texture is RGBA8, so GL swizzles during the transfer.
        GL11.glTexSubImage2D(
            GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L,
        )
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
    }

    /** Skia deletes [texture] itself once it adopted it. */
    fun close(deleteTexture: Boolean) {
        if (deleteTexture) GL11.glDeleteTextures(texture)
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo)
        GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
        GL15.glDeleteBuffers(pbo)
    }

    private companion object {
        var loaded = false

        fun ensureCapabilities() {
            if (!loaded) {
                GL.create()
                loaded = true
            }
            // Per context; cheap enough to repeat.
            GL.createCapabilities()
        }
    }
}

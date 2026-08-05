package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.FPDFBitmap_BGRA
import com.dshatz.internal.pdfium.FPDFBitmap_CreateEx
import com.dshatz.internal.pdfium.FPDFBitmap_Destroy
import com.dshatz.internal.pdfium.FPDFBitmap_FillRect
import com.dshatz.internal.pdfium.FPDF_BITMAP
import com.dshatz.internal.pdfium.FPDF_ClosePage
import com.dshatz.internal.pdfium.FPDF_GetLastError
import com.dshatz.internal.pdfium.FPDF_GetPageCount
import com.dshatz.internal.pdfium.FPDF_GetPageHeightF
import com.dshatz.internal.pdfium.FPDF_GetPageWidthF
import com.dshatz.internal.pdfium.FPDF_InitLibrary
import com.dshatz.internal.pdfium.FPDF_LoadCustomDocument
import com.dshatz.internal.pdfium.FPDF_LoadDocument
import com.dshatz.internal.pdfium.FPDF_LoadMemDocument
import com.dshatz.internal.pdfium.FPDF_LoadPage
import com.dshatz.internal.pdfium.FPDF_PAGE
import com.dshatz.internal.pdfium.FPDF_RenderPageBitmap
import com.dshatz.internal.pdfium.FPDF_RenderPageBitmapWithMatrix
import com.dshatz.internal.pdfium.FS_MATRIX
import com.dshatz.internal.pdfium.FS_RECTF
import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.error.FileError
import com.dshatz.pdfmp.error.PdfiumException
import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.BufferInfo
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeNullPtr
import kotlinx.cinterop.pin
import kotlinx.cinterop.toCPointer

@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer: AutoCloseable {

    private val drawTileFrames: Boolean = false

    private val customSourceDescriptor: StableRef<CustomPdfSourceAdapter>?
    private val source: PdfSource?
    actual constructor(source: PdfSource) {
        this.source = source
        this.customSourceDescriptor = null
        if (source is PdfSource.PdfBytes) {
            pinnedData = source.bytes.pin()
        }
    }

    actual constructor(source: CustomPdfSourceAdapter) {
        d("Init with custom source (native)")
        this.customSourceDescriptor = StableRef.create(source)
        this.source = null
    }

    private var pinnedData: Pinned<ByteArray>? = null

    private val pdfLock = ReentrantLock()

    companion object {
        fun init() {
            FPDF_InitLibrary()
        }
    }

    init {
        init()
    }

    private lateinit var doc: DocInstance


    private fun createDocumentHandle(): CPointer<fpdf_document_t__> {
        d("createDocumentHandle native")
        val handle: CPointer<fpdf_document_t__>? = when (val src = source) {
            is PdfSource.PdfBytes -> {
                FPDF_LoadMemDocument(pinnedData!!.addressOf(0), pinnedData!!.get().size, null)
            }
            is PdfSource.PdfPath -> {
                if (checkFilePath(src.path)) {
                    FPDF_LoadDocument(src.path.toString(), null)
                } else throw FileError()
            }
            null -> {
                if (customSourceDescriptor != null) {
                    val fileAccess = createFileAccessFromSource(customSourceDescriptor).getOrThrow()
                    FPDF_LoadCustomDocument(fileAccess.pdfiumAccess, null)
                } else null
            }
        }

        if (handle == null || handle.rawValue == nativeNullPtr) {
            val pdfErrorCode = FPDF_GetLastError().toByte()
            val customSourceError = getLastErrorForCustomSource()
            val pdfiumError = PdfiumException.getError(pdfErrorCode) ?: RuntimeException("Failed to open PDF handle")
            if (customSourceError != null) {
                throw RuntimeException(customSourceError, cause = pdfiumError)
            } else throw pdfiumError
        }

        return handle
    }

    @JniCall
    @OptIn(UnsafeNumber::class)
    actual fun openDocument(): Result<Unit> {
        return runCatching {
            doc = DocInstance(createDocumentHandle())
        }.onFailure {
            it.printStackTrace()
        }
    }

    private fun BufferDimensions.fpdfBitmap(bufferAddress: Long): FPDF_BITMAP {
        val targetPtr: CPointer<ByteVar> = bufferAddress.toCPointer<ByteVar>()
            ?: throw IllegalArgumentException("Invalid target memory address")
        return FPDFBitmap_CreateEx(
            width,
            height,
            FPDFBitmap_BGRA,
            targetPtr,
            stride
        ) ?: throw IllegalStateException("Failed to create combined bitmap wrapper")
    }

    @JniCall
    actual fun renderTileAsync(tile: PdfTile, bufferInfo: BufferInfo, callback: TileRenderCallback) = pdfLock.withLock {
        val bitmap = bufferInfo.dimensions.fpdfBitmap(bufferInfo.address)

        val page = doc.handle.openPage(tile.key.page)
        val result = runCatching {
            memScoped {
                val pageW: Float = FPDF_GetPageWidthF(page)
                val pageH: Float = FPDF_GetPageHeightF(page)
                val matrix = cValue<FS_MATRIX> {
                    this.a = tile.scaledPage.width / pageW
                    this.b = 0f
                    this.c = 0f
                    this.d = tile.scaledPage.height / pageH
                    this.e = -tile.key.x.toFloat()
                    this.f = -tile.key.y.toFloat()
                }
                val clip = cValue<FS_RECTF> {
                    this.left = 0.0f
                    this.top = 0.0f
                    this.right = PdfTile.WIDTH.toFloat()
                    this.bottom = PdfTile.HEIGHT.toFloat()

                }

                val bgWidth = (tile.scaledPage.width - tile.key.x).coerceIn(0, PdfTile.WIDTH)
                val bgHeight = (tile.scaledPage.height - tile.key.y).coerceIn(0, PdfTile.HEIGHT)

                FPDFBitmap_FillRect(
                    bitmap,
                    0,
                    0,
                    PdfTile.WIDTH,
                    PdfTile.HEIGHT,
                    0x00000000u // transparent black to reset the pixels already in the buffer.
                )

                FPDFBitmap_FillRect(
                    bitmap,
                    0,
                    0,
                    bgWidth,
                    bgHeight,
                    0xFFFFFFFFu
                )
                FPDF_RenderPageBitmapWithMatrix(
                    bitmap,
                    page,
                    matrix,
                    clip,
                    0
                )
                if (drawTileFrames) {
                    bitmap.drawFrame(
                        PdfTile.WIDTH,
                        PdfTile.HEIGHT,
                        color = if (tile.key.page % 2 == 0) 0xFF0000FFu else 0xFFFF0000u
                    )
                }
            }
        }
        page.closePage()
        result.onFailure { callback.onFailure(it.message ?: "Unknown error") }
            .onSuccess { callback.onSuccess() }
        callback.close()
        FPDFBitmap_Destroy(bitmap)
    }

    /**
     * Draws a 1-pixel (or customizable thickness) frame around the perimeter of the bitmap.
     *
     * @param color ARGB color format (e.g., 0xFF0000FFu for Blue, 0xFFFF0000u for Red)
     * @param strokeWidth Border width in pixels
     */
    private fun FPDF_BITMAP.drawFrame(
        width: Int,
        height: Int,
        color: UInt = 0xFF0000FFu, // Default: Solid Blue
        strokeWidth: Int = 2
    ) {
        // Top border
        FPDFBitmap_FillRect(this, 1, 1, width, strokeWidth, color.convert())
        // Bottom border
        FPDFBitmap_FillRect(this, 1, height - strokeWidth - 1, width, strokeWidth, color.convert())
        // Left border
        FPDFBitmap_FillRect(this, 1, 1, strokeWidth, height, color.convert())
        // Right border
        FPDFBitmap_FillRect(this, width - strokeWidth - 1, 1, strokeWidth, height, color.convert())
    }

    @JniCall
    actual fun renderAsync(renderRequest: RenderRequest, callback: RenderCallback) {
        val dimensions = renderRequest.dimensions
        val bitmap = renderRequest.bufferInfo.dimensions.fpdfBitmap(renderRequest.bufferInfo.address)

        val page = doc.handle.openPage(renderRequest.page)
        val result = runCatching {
            FPDFBitmap_FillRect(
                bitmap,
                0,
                0,
                dimensions.width,
                dimensions.height,
                0xFFFFFFFFu
            )
            FPDF_RenderPageBitmap(
                bitmap,
                page,
                0,
                0,
                dimensions.width,
                dimensions.height,
                0,
                0
            )
        }
        page.closePage()
        result.onFailure { callback.onFailure(it.message ?: "Unknown error") }
            .onSuccess { callback.onSuccess() }
        callback.close()
        FPDFBitmap_Destroy(bitmap)
    }

    private fun getLastErrorForCustomSource(): String? {
        return null // TODO
//        return (source as? PdfSource.Custom)?.customSourceDescriptor?.sourceAdapter?.getLastError()
    }

    @JniCall
    @OptIn(UnsafeNumber::class)
    actual fun getPageCount(callback: PdfOperationCallback) {
        callback.onPageCount(runCatching {
            FPDF_GetPageCount(doc.handle)
        })
        callback.close()
    }

    @OptIn(UnsafeNumber::class)
    private fun CPointer<fpdf_document_t__>.openPage(pageIndex: Int): FPDF_PAGE {
        return FPDF_LoadPage(this, pageIndex)
            ?: run {
                error("Failed to load page $pageIndex: ${getLastErrorForCustomSource() ?: FPDF_GetLastError()}")
            }
    }

    private fun FPDF_PAGE.closePage() {
        FPDF_ClosePage(this)
    }

    /**
     * Call this when the Screen/Component is destroyed
     */
    actual override fun close() {
        runCatching {
            if (::doc.isInitialized) {
                doc.close()
            }
            pinnedData?.unpin()
            source?.dispose()
            customSourceDescriptor?.get()?.close()
            customSourceDescriptor?.dispose()
        }.onFailure {
            e("Could not close document", it)
        }
    }

    @JniCall
    actual fun getPageRatio(pageIndex: Int, callback: PdfOperationCallback) {

        val doc = doc.handle

        val result = runCatching {
            val page = doc.openPage(pageIndex)
            try {
                val width = FPDF_GetPageWidthF(page)
                val height = FPDF_GetPageHeightF(page)
                if (width <= 0f || height <= 0f) {
                    error("Invalid size: $width x $height")
                }
                width / height
            } finally {
                page.closePage()
            }
        }.recoverCatching {
            val customSourceError = getLastErrorForCustomSource()
            if (customSourceError != null) {
                error(customSourceError)
            } else throw it
        }
        callback.onPageRatio(result)
        callback.close()
    }
}

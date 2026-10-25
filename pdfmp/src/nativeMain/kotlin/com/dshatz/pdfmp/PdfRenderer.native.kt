package com.dshatz.pdfmp

import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.error.FileError
import com.dshatz.pdfmp.error.PdfiumException
import com.dshatz.pdfmp.imagebuffer.ImageBuffer
import com.dshatz.pdfmp.imagebuffer.WritableImageBuffer
import com.dshatz.pdfmp.pdfium.DataPointer
import com.dshatz.pdfmp.pdfium.DocHandle
import com.dshatz.pdfmp.pdfium.FileAccess
import com.dshatz.pdfmp.pdfium.PageHandle
import com.dshatz.pdfmp.pdfium.Pdfium
import com.dshatz.pdfmp.pdfium.PdfiumBitmap
import com.dshatz.pdfmp.pdfium.PdfiumRenderCore
import com.dshatz.pdfmp.pdfium.close
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.pin
import kotlinx.cinterop.toLong


@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer: PdfiumRenderer {

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
        this.customSourceDescriptor = StableRef.create(source)
        this.source = null
    }

    private var pinnedData: Pinned<ByteArray>? = null

    companion object {
        fun init() {
            Pdfium.InitLibrary()
        }
    }

    init {
        init()
    }


    private suspend fun createDocumentHandle(): DocHandle {
        val handle: DocHandle? = when (val src = source) {
            is PdfSource.PdfBytes -> {
                Pdfium.LoadMemDocument(DataPointer(pinnedData!!), pinnedData!!.get().size, null)
            }
            is PdfSource.PdfPath -> {
                if (checkFilePath(src.path)) {
                    Pdfium.LoadDocument(src.path.toString(), null)
                } else throw FileError()
            }
            null -> {
                if (customSourceDescriptor != null) {
                    val fileAccess = createFileAccessFromSource(customSourceDescriptor).getOrThrow()
                    Pdfium.LoadCustomDocument(FileAccess(fileAccess), null)
                } else null
            }
        }

        if (handle == null) {
            val pdfErrorCode = Pdfium.GetLastError().toByte()
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
    actual override suspend fun openDocument(): Result<Unit> {
        return runCatching {
            d("Source: $source")
            doc = createDocumentHandle()
        }.onFailure {
            it.printStackTrace()
        }
    }

    /** Wraps [this] buffer's memory in a pdfium bitmap, so pdfium renders straight into it. */
    private fun ImageBuffer.withFpdfBitmap(block: (bitmap: PdfiumBitmap) -> Unit) {
        try {
            (this as WritableImageBuffer).withWritableAddress {
                val bitmap = fpdfBitmap(it.toLong())
                try {
                    block(bitmap)
                } finally {
                    Pdfium.BitmapDestroy(bitmap)
                }
            }
        } finally {
            (this as? AutoCloseable)?.close()
        }
    }

    override suspend fun renderTileInto(buffer: ImageBuffer, page: PageHandle, tile: PdfTile, frameColor: UInt?) {
        buffer.withFpdfBitmap { bitmap ->
            PdfiumRenderCore.renderTile(
                Pdfium,
                bitmap,
                page,
                tile.key.x,
                tile.key.y,
                PdfTile.WIDTH,
                PdfTile.HEIGHT,
                tile.scaledPage.width,
                tile.scaledPage.height,
                frameColor
            )
        }
    }

    override suspend fun renderPageInto(buffer: ImageBuffer, page: PageHandle, dimensions: PageDimensions) {
        buffer.withFpdfBitmap { bitmap ->
            PdfiumRenderCore.renderPage(Pdfium, bitmap, page, dimensions.width, dimensions.height)
        }
    }

    override suspend fun pageRatio(page: PageHandle): Float = PdfiumRenderCore.pageRatio(Pdfium, page)

    override suspend fun DocHandle.openPage(pageIndex: Int): PageHandle? {
        return Pdfium.LoadPage(doc, pageIndex)
    }

    override fun PageHandle.closePage() {
        Pdfium.ClosePage(this)
    }

    /**
     * Call this when the Screen/Component is destroyed
     */
    actual override fun close() {
        runCatching {
            pageHandleCache.forEach {
                it.value.closePage()
            }
            pageHandleCache.clear()

            if (isDocInitialized()) {
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
}

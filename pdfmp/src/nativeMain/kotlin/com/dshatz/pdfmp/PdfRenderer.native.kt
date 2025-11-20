package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.*
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.*
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer actual constructor(private val source: PdfSource): SynchronizedObject() {

    private var doc: CPointer<fpdf_document_t__>? = null
    private var pinnedData: Pinned<ByteArray>? = null

    companion object {
        fun init() {
            FPDF_InitLibrary()
        }
    }

    @OptIn(UnsafeNumber::class)
    fun openFile() = synchronized(this) {
        runCatching {
            doc = when (source) {
                is PdfSource.PdfBytes -> {
                    pinnedData = source.bytes.pin()
                    FPDF_LoadMemDocument(
                        pinnedData!!.addressOf(0),
                        source.bytes.size,
                        null
                    )
                }
                is PdfSource.PdfPath -> FPDF_LoadDocument(source.path.toString(), null)
            }
        }
        if (doc == null || doc.rawValue == nativeNullPtr) {
            // The document failed to load. Log an error or throw an exception.
            println("ERROR: FPDF_DOCUMENT failed to load. Check file path/integrity/password. ${FPDF_GetLastError()}")
        }
    }

    @OptIn(UnsafeNumber::class)
    actual fun render(renderRequest: RenderRequest): RenderResponse = synchronized(this) {
        if (doc == null) {
            val error = FPDF_GetLastError()
            println("Failed to load PDF from $source. Error code: $error")
            throw IllegalStateException("Failed to load PDF from $source. Error code: $error")
        }

        return renderPage(
            renderRequest.page,
            renderRequest.transform,
            renderRequest.bufferAddress
        )
    }

    actual fun getPageCount(): Int = synchronized(this) {
        runCatching {
            val document = doc ?: throw IllegalStateException("Document is not open")
            return FPDF_GetPageCount(document)
        }.getOrElse {
            println("Could not get page count: ${it.message}")
            0
        }
    }

    @OptIn(UnsafeNumber::class)
    private fun CPointer<fpdf_document_t__>.openPage(pageIndex: Int): FPDF_PAGE? {
        return runCatching {
            FPDF_LoadPage(this, pageIndex)
                ?: error("Failed to load page $pageIndex. Error: ${FPDF_GetLastError()}")
        }.getOrElse {
            println(it.message)
            return null
        }
    }

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun renderPage(
        pageIndex: Int,
        transform: ImageTransform,
        bufferAddress: Long
    ): RenderResponse {
        return runCatching {
            val document = doc ?: throw IllegalStateException("Document is not open")

            val scale = transform.scale
            val viewportWidth = transform.viewportWidth
            val viewportHeight = transform.viewportHeight
            val offsetX = transform.offsetX
            val offsetY = transform.offsetY

            val page = document.openPage(pageIndex)

            try {
                val unscaledPageWidth = FPDF_GetPageWidthF(page)
                val unscaledPageHeight = FPDF_GetPageHeightF(page)
                val rawPageWidth = (unscaledPageWidth * scale).toInt()
                val rawPageHeight = (unscaledPageHeight * scale).toInt()

                if (viewportWidth <= 0 || viewportHeight <= 0) {
                    throw IllegalStateException("Invalid page dimensions: $viewportWidth x $viewportHeight")
                }

                val fitScale = viewportWidth.toFloat() / rawPageWidth
                val finalScale = scale * fitScale

                val totalScaledWidth = (rawPageWidth * finalScale).toInt()
                val totalScaledHeight = (rawPageHeight * finalScale).toInt()

                val bitmap = FPDFBitmap_CreateEx(viewportWidth, viewportHeight, FPDFBitmap_BGRx, null, 0)
                    ?: throw IllegalStateException("Failed to create bitmap")

                try {
                    FPDFBitmap_FillRect(bitmap, 0, 0, viewportWidth, viewportHeight, 0xFFFFFFFFu)
                    FPDF_RenderPageBitmap(
                        bitmap,
                        page,
                        offsetX.toInt(),
                        offsetY.toInt(),
                        totalScaledWidth,
                        totalScaledHeight,
                        0,
                        0
                    )

                    val bufferPtr: CPointer<out CPointed> = FPDFBitmap_GetBuffer(bitmap)?.reinterpret()
                        ?: throw IllegalStateException("Failed to get bitmap buffer")

                    val byteCount = viewportWidth * viewportHeight * 4
                    memScoped {
                        val targetPtr: CPointer<out CPointed> = bufferAddress.toCPointer<CPointed>()
                            ?: throw IllegalArgumentException("Invalid target memory address")
                        memcpy(targetPtr, bufferPtr, byteCount.convert())
                    }
//                    val pixelData: ByteArray = bufferPtr.readBytes(byteCount)

                    return RenderResponse(transform, PageSize(unscaledPageWidth, unscaledPageHeight))

                } finally {
                    FPDFBitmap_Destroy(bitmap)
                }
            } finally {
                FPDF_ClosePage(page)
            }
        }.getOrElse {
            println("Could not render page ${it.message}")
            throw(it)
        }
    }

    // You should call this when the Screen/Component is destroyed
    actual fun close() = synchronized(this) {
        runCatching {
            if (doc != null) {
                FPDF_CloseDocument(doc)
                pinnedData?.unpin()
                doc = null
            }
        }.onFailure {
            println("Could not close document: ${it.message}")
        }
        Unit
        // FPDF_DestroyLibrary() // Call this only when app exits strictly
    }

    actual fun getAspectRatio(pageIndex: Int): Float = synchronized(this) {
        return runCatching {
            val document = doc ?: error("Document is not open")

            val page = document.openPage(pageIndex)

            try {
                val width = FPDF_GetPageWidthF(page)
                val height = FPDF_GetPageHeightF(page)

                if (width <= 0f || height <= 0f) {
                    return@runCatching 1f
                }
                width / height

            } finally {
                FPDF_ClosePage(page)
            }
        }.getOrElse { e ->
            println("Native Error getting aspect ratio: ${e.message}")
            0.707f
        }
    }

    actual fun getPageRatios(): List<Float> = synchronized(this) {
        val pageCount = getPageCount()
        (0..<pageCount).map {
            getAspectRatio(it)
        }
    }
}


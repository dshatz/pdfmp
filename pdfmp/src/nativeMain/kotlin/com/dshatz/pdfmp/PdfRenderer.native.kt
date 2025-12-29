package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.*
import com.dshatz.pdfmp.error.PdfiumException
import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer(private val source: PdfSource): SynchronizedObject() {

    private var doc: CPointer<fpdf_document_t__>? = null
    private var pinnedData: Pinned<ByteArray>? = null

    companion object {
        fun init() {
            FPDF_InitLibrary()
        }
    }

    @OptIn(UnsafeNumber::class)
    fun openFile(): Result<Unit> = synchronized(this) {
        return runCatching {
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
            if (doc == null || doc.rawValue == nativeNullPtr) {
                val pdfErrorCode = FPDF_GetLastError().toByte()
                PdfiumException.getError(pdfErrorCode)?.let { throw it }
            }
        }
    }

    @OptIn(UnsafeNumber::class)
    actual suspend fun render(renderRequest: RenderRequest): Result<RenderResponse> = synchronized(this) {
        runCatching {
            renderPages(
                renderRequest.transforms,
                renderRequest.bufferInfo.address,
                renderRequest.bufferInfo.dimensions
            )
        }
    }

    actual fun getPageCount(): Result<Int> = synchronized(this) {
        runCatching {
            val document = getDocument()
            FPDF_GetPageCount(document)
        }
    }

    @OptIn(UnsafeNumber::class)
    private fun CPointer<fpdf_document_t__>.openPage(pageIndex: Int): FPDF_PAGE? {
        return FPDF_LoadPage(this, pageIndex)
            ?: error("Failed to load page $pageIndex. Error: ${FPDF_GetLastError()}")
    }

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun renderPages(
        transforms: List<PageTransform>,
        bufferAddress: Long,
        bufferDimensions: BufferDimensions
    ): RenderResponse {
        val document = getDocument()

        var totalHeight = 0
        var maxWidth = 0

        transforms.forEachIndexed { index, it ->
            val h = it.scaledHeight - it.topCutoff - it.bottomCutoff
            val w = it.scaledWidth - it.leftCutoff - it.rightCutoff
            if (h <= 0 || w <= 0) error("Invalid slice dimensions")
            totalHeight += h + it.topGap
            maxWidth = maxOf(maxWidth, w)
        }

        if (totalHeight == 0 || maxWidth == 0 || bufferDimensions.stride == 0) error("Total dimensions are zero")

        val targetPtr: CPointer<ByteVar> = bufferAddress.toCPointer<ByteVar>()
            ?: throw IllegalArgumentException("Invalid target memory address")

        val combinedBitmap = FPDFBitmap_CreateEx(
            bufferDimensions.width,
            bufferDimensions.height,
            FPDFBitmap_BGRA,
            targetPtr,
            bufferDimensions.stride
        ) ?: throw IllegalStateException("Failed to create combined bitmap wrapper")

        try {
            FPDFBitmap_FillRect(
                combinedBitmap,
                0,
                0,
                bufferDimensions.width,
                bufferDimensions.height,
                0x00000000u
            )

            var currentY = 0

            transforms.forEach { transform ->
                currentY += transform.topGap

                val sliceHeight = transform.scaledHeight - transform.topCutoff - transform.bottomCutoff
                val sliceWidth = transform.scaledWidth - transform.leftCutoff - transform.rightCutoff

                FPDFBitmap_FillRect(
                    combinedBitmap,
                    0,
                    currentY,
                    sliceWidth,
                    sliceHeight,
                    0xFFFFFFFFu
                )

                val page = document.openPage(transform.pageIndex)
                try {
                    val startX = -transform.leftCutoff
                    val startY = currentY - transform.topCutoff

                    FPDF_RenderPageBitmap(
                        combinedBitmap,
                        page,
                        startX,
                        startY,
                        transform.scaledWidth,
                        transform.scaledHeight,
                        0,
                        0
                    )
                } finally {
                    FPDF_ClosePage(page)
                }
                currentY += sliceHeight
            }
            return RenderResponse(transforms)

        } finally {
            FPDFBitmap_Destroy(combinedBitmap)
        }
    }

    /**
     * Call this when the Screen/Component is destroyed
     */
    actual fun close() = synchronized(this) {
        runCatching {
            if (doc != null) {
                FPDF_CloseDocument(doc)
                pinnedData?.unpin()
                doc = null
            }
        }.onFailure {
            e("Could not close document", it)
        }
        Unit
    }

    fun getAspectRatio(pageIndex: Int): Float = synchronized(this) {
        return runCatching {
            val document = getDocument()

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
            e("Native Error getting aspect ratio", e)
            0.707f
        }
    }

    actual fun getPageRatios(): Result<List<Float>> = synchronized(this) {
        val pageCount = getPageCount()
        pageCount.mapCatching { pageCount ->
            (0..<pageCount).map {
                getAspectRatio(it)
            }
        }
    }

    private fun getDocument(): CPointer<fpdf_document_t__> {
        return doc ?: throw IllegalStateException("Document is not open")
    }
}

actual object PdfRendererFactory {
    actual suspend fun createFromSource(source: PdfSource): Result<PdfRenderer> {
        val renderer = PdfRenderer(source)
        return renderer.openFile().map { renderer }
    }
}
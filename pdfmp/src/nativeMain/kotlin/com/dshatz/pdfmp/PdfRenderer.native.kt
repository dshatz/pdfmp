package com.dshatz.pdfmp

import cnames.structs.fpdf_document_t__
import com.dshatz.internal.pdfium.FPDFBitmap_BGRA
import com.dshatz.internal.pdfium.FPDFBitmap_CreateEx
import com.dshatz.internal.pdfium.FPDFBitmap_Destroy
import com.dshatz.internal.pdfium.FPDFBitmap_FillRect
import com.dshatz.internal.pdfium.FPDF_CloseDocument
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
import com.dshatz.kni.annotations.JniCall
import com.dshatz.pdfmp.error.FileError
import com.dshatz.pdfmp.error.PdfiumException
import com.dshatz.pdfmp.model.BufferDimensions
import com.dshatz.pdfmp.model.PageTransform
import com.dshatz.pdfmp.model.RenderRequest
import com.dshatz.pdfmp.model.RenderResponse
import com.dshatz.pdfmp.progressive.renderPageProgressively
import com.dshatz.pdfmp.source.CustomPdfSourceAdapter
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.nativeNullPtr
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.toCPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalForeignApi::class)
actual class PdfRenderer: AutoCloseable {

    private val customSourceDescriptor: CustomPdfSourceAdapter?
    private val source: PdfSource?
    actual constructor(source: PdfSource) {
        this.source = source
        this.customSourceDescriptor = null
    }

    actual constructor(source: CustomPdfSourceAdapter) {
        this.customSourceDescriptor = source
        this.source = null
    }

    private var doc: CPointer<fpdf_document_t__>? = null
    private var pinnedData: Pinned<ByteArray>? = null

    private val pdfLock = ReentrantLock()

    private val rendererScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        fun init() {
            FPDF_InitLibrary()
        }
    }

    init {
        init()
    }


    @JniCall
    @OptIn(UnsafeNumber::class)
    actual fun openDocument(): Result<Unit> {
        return runCatching {
            if (source != null) {
                when (source) {
                    is PdfSource.PdfBytes -> {
                        pinnedData = source.bytes.pin()
                        doc = FPDF_LoadMemDocument(
                            pinnedData!!.addressOf(0),
                            source.bytes.size,
                            null
                        )
                    }
                    is PdfSource.PdfPath -> {
                        if (checkFilePath(source.path)) {
                            doc = FPDF_LoadDocument(source.path.toString(), null)
                        } else throw FileError()
                    }
                }
            } else if (customSourceDescriptor != null) {
                val fileAccess = createFileAccessFromSource(customSourceDescriptor)
                fileAccess.map {
                    doc = FPDF_LoadCustomDocument(it.pdfiumAccess, null)
                }
            }
            if (doc == null || doc.rawValue == nativeNullPtr) {
                val pdfErrorCode = FPDF_GetLastError().toByte()
                val exception = PdfiumException.getError(pdfErrorCode) ?: RuntimeException("doc is null")
                throw exception
            }
        }
    }

    @OptIn(UnsafeNumber::class)
    private suspend fun render(renderRequest: RenderRequest): Result<RenderResponse> {
        val renderResponse = runCatching {
            renderPages(
                renderRequest.transforms,
                renderRequest.bufferInfo.address,
                renderRequest.bufferInfo.dimensions
            )
        }.recoverCatching {
            val customSourceError = getLastErrorForCustomSource()
            if (customSourceError != null) {
                error(customSourceError)
            } else throw it
        }


         return renderResponse
    }

    @JniCall
    actual fun renderAsync(renderRequest: RenderRequest, callback: RenderCallback) {
        rendererScope.launch {
            val renderResponse = runCatching {
                renderPages(
                    renderRequest.transforms,
                    renderRequest.bufferInfo.address,
                    renderRequest.bufferInfo.dimensions
                )
            }.recoverCatching {
                val customSourceError = getLastErrorForCustomSource()
                if (customSourceError != null) {
                    error(customSourceError)
                } else throw it
            }
            renderResponse.onSuccess {
                callback.onSuccess(it)
            }.onFailure { callback.onFailure(it.message ?: "Unknown render error") }
        }
    }

    private fun getLastErrorForCustomSource(): String? {
        return "" // TODO
//        return (source as? PdfSource.Custom)?.customSourceDescriptor?.sourceAdapter?.getLastError()
    }

    @OptIn(UnsafeNumber::class)
    actual fun getPageCount(): Result<Int> {
        return runCatching {
            val document = getDocument()
            if (doc == null || document.rawValue.toLong() == 0L) {
                return Result.failure(Exception("Document handle is NULL before calling PageCount"))
            }

            FPDF_GetPageCount(document)
        }
    }

    @OptIn(UnsafeNumber::class)
    private fun CPointer<fpdf_document_t__>.openPage(pageIndex: Int): FPDF_PAGE = pdfLock.withLock {
        return FPDF_LoadPage(this, pageIndex)
            ?: run {
                error("Failed to load page $pageIndex: ${getLastErrorForCustomSource() ?: FPDF_GetLastError()}")
            }
    }

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private suspend fun renderPages(
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

                    renderPageProgressively(
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
    actual override fun close() {
        runCatching {
            if (doc != null) {
                FPDF_CloseDocument(doc)
                pinnedData?.unpin()
                doc = null
                source?.dispose()
                customSourceDescriptor?.close()
                rendererScope.coroutineContext[Job]?.cancel()
            }
        }.onFailure {
            e("Could not close document", it)
        }
        Unit
    }

    private fun getAspectRatio(pageIndex: Int): Float {
        val document = getDocument()

        val page = document.openPage(pageIndex)

        return try {
            val width = FPDF_GetPageWidthF(page)
            val height = FPDF_GetPageHeightF(page)

            if (width <= 0f || height <= 0f) {
                error("Invalid size: $width x $height")
            }
            width / height

        } finally {
            FPDF_ClosePage(page)
        }
    }

    actual fun getPageRatio(pageIndex: Int): Result<Float> {
        return runCatching {
            getAspectRatio(pageIndex)
        }.recoverCatching {
            val customSourceError = getLastErrorForCustomSource()
            if (customSourceError != null) {
                error(customSourceError)
            } else throw it
        }
    }

    private fun getDocument(): CPointer<fpdf_document_t__> {
        return doc ?: throw IllegalStateException("Document is not open")
    }
}

/*
actual object PdfRendererFactory {
    actual fun createFromSource(source: PdfSource): Result<PdfRenderer> {
        val renderer = PdfRenderer(source, null)
        return renderer.openFile().map { renderer }
    }

    actual fun createFromCustomSource(source: CustomPdfSourceAdapter): Result<PdfRenderer> {
        val renderer = PdfRenderer(null, source)
        return renderer.openFile().map { renderer }
    }
}
*/

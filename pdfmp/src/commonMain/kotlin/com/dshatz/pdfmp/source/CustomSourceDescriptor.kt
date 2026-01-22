package com.dshatz.pdfmp.source

/**
 * Represents information that is necessary to use a custom PDF source.
 *
 * Definitions differ per platform.
 */
expect class CustomSourceDescriptor {
    fun dispose()
}
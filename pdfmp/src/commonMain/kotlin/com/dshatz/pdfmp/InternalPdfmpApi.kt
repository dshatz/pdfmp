package com.dshatz.pdfmp

/** Internal plumbing between pdfmp modules and its native code. Apps open documents with [PdfData] instead. */
@RequiresOptIn("Internal pdfmp API. Open documents with PdfData instead.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY)
annotation class InternalPdfmpApi

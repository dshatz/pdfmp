package com.dshatz.pdfmp

/** What opening a [PdfData] may need from the platform: the Android `Context`; nothing elsewhere. */
expect abstract class PlatformContext

/** The [PlatformContext] of platforms that need nothing; null on Android, which needs its Context. */
@InternalPdfmpApi
expect val noPlatformContext: PlatformContext?

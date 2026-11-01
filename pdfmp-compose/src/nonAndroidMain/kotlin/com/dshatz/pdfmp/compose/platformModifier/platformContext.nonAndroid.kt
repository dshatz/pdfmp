package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.Composable
import com.dshatz.pdfmp.PlatformContext
import com.dshatz.pdfmp.noPlatformContext

@Composable
internal actual fun rememberPlatformContext(): PlatformContext = noPlatformContext!!

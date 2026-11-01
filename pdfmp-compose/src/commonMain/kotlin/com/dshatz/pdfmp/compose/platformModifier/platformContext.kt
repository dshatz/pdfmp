package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.Composable
import com.dshatz.pdfmp.PlatformContext

@Composable
internal expect fun rememberPlatformContext(): PlatformContext

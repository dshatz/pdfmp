package com.dshatz.pdfmp.compose.platformModifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dshatz.pdfmp.PlatformContext

@Composable
internal actual fun rememberPlatformContext(): PlatformContext = LocalContext.current.applicationContext

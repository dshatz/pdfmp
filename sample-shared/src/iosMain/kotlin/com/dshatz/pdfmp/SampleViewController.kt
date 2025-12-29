package com.dshatz.pdfmp

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun SampleViewController(): UIViewController =
    ComposeUIViewController(configure = {
        enforceStrictPlistSanityCheck = false
    }) {
//        Text("Hello iOS")
        LaunchedEffect(Unit) {
            d("Hello ios")
        }
        Sample()
    }
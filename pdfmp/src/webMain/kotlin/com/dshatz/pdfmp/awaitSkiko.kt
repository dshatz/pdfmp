@file:JsModule("./skiko.mjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.pdfmp

import kotlin.js.Promise

external val awaitSkiko: Promise<JsAny?>
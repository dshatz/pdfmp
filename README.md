# PDF viewer for Compose Multiplatform & Kotlin Multiplatform: Android, iOS, Desktop (JVM) and Web (Wasm). Powered by pdfium.

![](https://img.shields.io/badge/Linux%20x64/arm64-blue?style=for-the-badge&logo=linux)
![](https://img.shields.io/badge/MacOS%20x64/arm64-blue?style=for-the-badge&logo=apple)
![](https://img.shields.io/badge/Windows%20(x64)-blue?style=for-the-badge&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz48IS0tIFVwbG9hZGVkIHRvOiBTVkcgUmVwbywgd3d3LnN2Z3JlcG8uY29tLCBHZW5lcmF0b3I6IFNWRyBSZXBvIE1peGVyIFRvb2xzIC0tPg0KPHN2ZyB3aWR0aD0iODAwcHgiIGhlaWdodD0iODAwcHgiIHZpZXdCb3g9IjAgMCAyNCAyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4NCiAgPHRpdGxlPm1pY3Jvc29mdF93aW5kb3dzPC90aXRsZT4NCiAgPHJlY3Qgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSJub25lIi8+DQogIDxwYXRoIGQ9Ik0zLDEyVjYuNzVMOSw1LjQzdjYuNDhMMywxMk0yMCwzdjguNzVMMTAsMTEuOVY1LjIxTDIwLDNNMywxM2w2LC4wOVYxOS45TDMsMTguNzVWMTNtMTcsLjI1VjIyTDEwLDIwLjA5di03WiIvPg0KPC9zdmc+)
![](https://img.shields.io/badge/Android%20(all%20eabis)-blue?style=for-the-badge&logo=android)
![](https://img.shields.io/badge/iOS%20(x64/arm64/simulator)-blue?style=for-the-badge&logo=ios)
![](https://img.shields.io/badge/WASM-green?style=for-the-badge&logo=webassembly)

pdfmp is a PDF viewer library for Compose Multiplatform. Show PDFs in Android, iOS, desktop and web apps with one PdfView composable: smooth zoom and scrolling, sharp tiled rendering, and progressive loading from URLs, files, Android content URIs or custom sources.

This library wraps [libpdfium](https://pdfium.googlesource.com/pdfium/) (the Chromium PDF engine). The pre-compiled pdfium files are taken from [here](https://github.com/bblanchon/pdfium-binaries) and bundled into this library.

## Supported targets
- Android
- JVM Desktop
    - Linux x64, ARM64
    - Windows X64
    - MacOS X64, ARM64
- wasmJs
- iOS
    - x64 (simulator)
    - arm64
    - arm64Simulator

 ## Usage
 ### Add the dependency
 ![Maven Central Version](https://img.shields.io/maven-central/v/com.dshatz.pdfmp/pdfmp)

 ```kotlin
 implementation("com.dshatz.pdfmp:pdfmp-compose:1.1.1")
```

```toml
pdfmp = { module = "com.dshatz.pdfmp:pdfmp-compose", version = "1.1.1" }
```

### Web (Kotlin/Wasm) setup

On the web, pdfium runs as WebAssembly inside a web worker, so pages render without blocking the UI.
The worker is wired in by the [Kotlin-WebWorker](https://github.com/dshatz/Kotlin-WebWorker) Gradle plugin.

**build.gradle.kts**
```kotlin
plugins {
    id("com.dshatz.jsworker") version "0.1.3"
}

kotlin {
    wasmJs {
        browser()
    }
    sourceSets {
        commonMain.dependencies {
            implementation("com.dshatz.pdfmp:pdfmp-compose:1.1.1")
        }
    }
}

dependencies {
    "wasmJsWorker"("com.dshatz.pdfmp:pdfium-wasm:1.1.1")
}
```

### Load the pdf
Documents are described by `PdfData`. Anything not entirely in memory or in a local file is read progressively:
only the parts needed for the pages on screen are read.

```kotlin
PdfData.bytes(byteArray)                              // in memory
PdfData.file(Path("/path/to/doc.pdf"))                // local file, read in place (on the web: a URL relative to the page)
PdfData.url("https://example.com/doc.pdf")            // HTTP range requests, or a full download if unsupported
PdfData.uri(Res.getUri("files/doc.pdf"))              // Compose resources, read in place where possible
PdfData.uri("content://...")                          // Android: e.g. from the system file picker
PdfData.fromStream(inputStream)                       // JVM and Android
PdfData.fromBlob(file)                                // web: <input type="file">, drag and drop
PdfData.randomAccess(mySource)                        // anything else, see PdfRandomAccess
```

On the web, `PdfData.url` needs a server that allows cross-origin requests (CORS).

### Display the document.
To create a `PdfState`, you need only a `PdfData`.

Additionally, you can pass some optional parameters:
 - `pageRange: IntRange` - range of pages to include. **Zero-indexed, inclusive range.**
 - `pageSpacing: Dp` - spacing between pages, **in DP**.

```kotlin

@Composable
fun ShowDoc() {
    val state = rememberPdfState(PdfData.uri(Res.getUri("files/doc.pdf")), pageSpacing = 100.dp)
    PdfView(state, Modifier.fillMaxSize())
}
```

#### Observing document state
Use `val displayState by state.displayState` to get the document state.

```kotlin
sealed class DisplayState {
    data object Initializing: DisplayState()
    data object Active: DisplayState()
    data class Error(val error: Throwable): DisplayState()
}
```

You can use this to display a loading indicator or an error dialog.


#### Controlling the document view
You can read and change various view parameters.
```kotlin
val layoutInfo by state.layoutInfo()
// It will be null if the document did not load yet.
layoutInfo?.apply {
    // Below is some of the available state

    val visiblePages: State<List<VisiblePageInfo>>
    val mostVisiblePage: State<VisiblePageInfo?>
    val totalPages: State<Int>
    val pageRange: State<IntRange>
    val documentHeight: State<Float>

    // Scroll related
    var offsetY: Float
    fun scrollTo(pageIdx: Int)
    suspend fun animateScrollTo(pageIdx: Int)

    // Zoom related
    val zoom: State<Float>
    fun setZoom(newZoom: Float)
    suspend fun animateSetZoom(newZoom: Float)
}
```

To build your own gestures or controls (e.g. drag to pan, zoom buttons around a point), `PdfState` has:
```kotlin
// Scroll by a pixel delta (positive y scrolls towards the end). Returns the part that was scrolled.
state.scrollBy(Offset(0f, 100f))
// Zoom by a factor, keeping the point under the centroid (relative to PdfView) in place.
state.zoomBy(zoomFactor = 1.25f, centroid = Offset(x, y))
```

## Features
| Feature                        | Supported | Notes                                                                                                                           |
| ------------------------------ | --------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Loading from bytes             | ✅        | `PdfData.bytes`. The whole document stays in memory.                                                                            |
| Loading from path              | ✅        | `PdfData.file`, read in place. On the web, a URL relative to the page (there is no file system).                  |
| Loading from compose resources | ✅        | `PdfData.uri(Res.getUri(…))`, read in place where possible (temporary copy for desktop jars and compressed Android assets).     |
| Loading from URL               | ✅        | `PdfData.url`: downloads only the parts needed (range requests), else the whole file. Needs CORS on the web.                    |
| Loading from content URI       | ✅        | Android: `PdfData.uri("content://…")`, e.g. from the system file picker.                                                        |
| Loading from stream            | ✅        | JVM and Android: `PdfData.fromStream`. File streams are read in place, others copied to a temporary file.                       |
| Loading from browser file      | ✅        | Web: `PdfData.fromBlob`, e.g. from `<input type="file">`. Only the parts needed are read.                                       |
| Custom sources                 | ✅        | `PdfData.randomAccess` with your own `PdfRandomAccess`. Only the parts needed are read.                                         |
| Zooming and scrolling          | ✅        | Loading the high-res image is debounced by 100ms. On desktop, horizontal scrolling can only be done with Shift+Vertical scroll. |
| Filtering pages                | ✅        | Pass page range to rememberPdfState                                                                                             |
| Text extraction                |           | Coming soon.

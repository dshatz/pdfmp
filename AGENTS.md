# pdfmp: a guide for agents

A Kotlin Multiplatform PDF viewer (JVM desktop, Android, iOS, web/wasm) on top of pdfium, with Compose UI.
Native code is written in Kotlin and exposed to the JVM through [Kotlin-JNI](https://dshatz.github.io/Kotlin-JNI/about.html); the `kni` Gradle plugin and its KSP processor generate the JNI glue). The web build runs pdfium
in a **web worker** ([Kotlin-WebWorker](https://github.com/dshatz/Kotlin-WebWorker) generates the worker proxies).

Use this file to find the right place before searching. Paths are relative to the repo root.

## Modules

| Module | What it is |
|---|---|
| `pdfmp-compose` | The public Compose API: `rememberPdfState`, `PdfView`, `PdfState` (scrolling, zoom, tile geometry). |
| `pdfmp` | Rendering and document loading: `PdfRenderer`, `PdfData` (public input type), the buffer pool. |
| `pdfium` | Thin pdfium wrapper (`Pdfium` object, handles, `PdfiumRenderCore`, `ProgressiveDocument`). cinterop on native, `Module._FPDF_*` on wasm. |
| `pdfium-wasm` | The web worker class `PdfiumWebWorker` (KSP generates `PdfiumWebWorkerWebWorker`, the main-thread proxy). |
| `pdfium-binaries` | Prebuilt pdfium libraries (not source). |
| `image-buffer` | `ImageBuffer`: pixel storage, one implementation per platform. |
| `pdfmp-wasmJs-plugin` | Gradle plugin that bundles worker assets for the web build. |
| `sample-shared`, `desktopapp`, `androidapp`, `webapp`, `iosapp` | Demo app: `sample-shared` holds the UI, the others are thin launchers. |

Static data: `pdfiumWasmJs/` has the pdfium wasm build (`pdfium.js`, `pdfium.wasm`).

## Source set hierarchy (`pdfmp` and `image-buffer`)

Defined in each module's `build.gradle.kts` (`applyHierarchyTemplate`). Pick the narrowest set that covers your code:

```
common
├─ pdfium            native + web: everything that talks to pdfium  (pdfiumMain)
│  ├─ native         desktopNative (linux/mingw/macos), androidNative, ios
│  └─ web            wasmJs
├─ jniCommon         code shared by both sides of a JNI call
│  ├─ jniNative      native side of JNI (desktopNative + androidNative)
│  └─ jniJvm         JVM side of JNI (jvm + android)
└─ consumer          what apps run on: jniJvm + ios + web
```

- `consumerMain` is where user-facing code goes. Native-only libraries (linux/mingw/macos/androidNative) are loaded by the JVM and are not consumers.
- `jvmMain` and `androidMain` split JVM from Android; `jniJvmMain` is shared by them.
- iOS code can't be compiled on Linux. Treat it as unverified.

## Where things are

Paths under `pdfmp/src/`, `pdfmp-compose/src/commonMain/kotlin/com/dshatz/pdfmp/compose/`, `pdfium/src/` use the package `com.dshatz.pdfmp[...]`.

**Opening a document (public API)**
- `pdfmp/src/consumerMain/.../PdfData.kt`: `PdfData` and its factories (`bytes`, `file`, `url`, `uri`, `randomAccess`); the internal implementations (`SourceData`, `UrlData`, `UriData`, ...).
- `pdfmp/src/consumerMain/.../PdfRandomAccess.kt`: user-implementable random-access source.
- `pdfmp/src/consumerMain/.../PdfHttp.kt` plus `PdfHttp` actuals in `PdfData.{jvm,android,ios,web}.kt`: the HTTP client per platform.
- `pdfmp/src/{jniJvmMain,androidMain,jvmMain,webMain,iosMain}/.../PdfData.*.kt`: platform factories and URI schemes (`fromStream`, `fromBlob`, `content://`, `jar:`).
- `pdfmp/src/consumerMain/.../PlatformContext.kt`: Android `Context`, nothing elsewhere.
- `pdfmp-compose/.../state/PdfState.kt`: `rememberPdfState(PdfData)` at the top of the file.

**Internal types (opt in with `@InternalPdfmpApi`)**: `pdfmp/src/commonMain/.../InternalPdfmpApi.kt`, `source/PdfSource.kt` (bytes or path, crosses JNI), `source/CustomPdfSourceAdapter.kt` (the JNI callback adapter).

**Rendering**
- `pdfmp/src/commonMain/.../PdfRenderer.kt`: the `expect class`. Actuals: `pdfmp/src/nativeMain/.../PdfRenderer.native.kt`, `pdfmp/src/webMain/.../PdfRenderer.wasmJs.kt`.
- `pdfmp/src/pdfiumMain/.../PdfiumRenderer.kt`: shared base of both actuals: page-handle cache, **progressive loading loop** (check, fetch missing chunks, check again), render dispatch.
- `pdfium/src/commonMain/.../PdfiumRenderCore.kt`: the pdfium calls for one tile or page render, run in-process on native and in the worker on web.
- `pdfmp/src/consumerMain/.../ImageBufferPool.kt`: buffer pool for tiles and page placeholders (also tracks buffers native code is still writing into).

**Progressive loading (FPDFAvail)**
- `pdfium/src/commonMain/.../pdfium/ProgressiveDocument.kt`: `Availability`, `ProgressiveChunks` (which chunks arrived), `ProgressiveCallbacks` (shared callback logic), `expect class ProgressiveDocument`.
- `pdfium/src/{nativeMain,webMain}/.../ProgressiveDocument.{native,web}.kt`: struct layouts and C function pointers per platform.
- `pdfium-wasm/.../PdfiumWebWorker.kt`: the `openProgressive` / `addChunks` / `isDocAvail` / ... worker methods.

**pdfium bindings**: `pdfium/src/commonMain/.../Pdfium.kt` (interface `IPdfium` plus `expect object Pdfium`), actuals in `pdfium/src/nativeMain/.../Pdfium.native.kt` and `pdfium/src/webMain/.../Pdfium.wasmJs.kt`, wasm externs in `pdfium/src/webMain/.../PdfiumWasm.kt`, cinterop headers in `pdfium/cinterop/pdfium/` (`pdfium.def` lists the headers).

**Compose UI**
- `pdfmp-compose/.../PdfView.kt`: `PdfView`, `FullPages` (low-resolution page placeholders), `TiledViewport` (sharp tiles, retired-tile logic, drawing).
- `pdfmp-compose/.../state/PdfState.kt`: scroll and zoom math (`onScroll`, `zoomBy`), `calculateVisibleTiles` (prefetch margins), `calculateVisiblePages`, `renderTile` / `renderFullPage`, `openDocument` lifecycle. `DisplayState.kt` has the public `PdfLayoutInfo`.
- `pdfmp-compose/.../platformModifier/`: per-platform scroll and gesture handling. `.../tools/`: drawing a buffer (`drawPixmap`) per platform.

**Image buffers**: `image-buffer/src/commonMain/.../ImageBuffer.kt` (`expect`), actuals: `jvmMain` (Skia `Pixmap`), `androidMain` (`Bitmap`), `desktopNativeMain`, `androidNativeMain`, `webMain` (Skia `Data`), `ImageBufferUtil.*` for allocation. The JNI adapters for passing buffers are in the same files.

**Web worker plumbing**: worker class `pdfium-wasm/.../PdfiumWebWorker.kt`; JS loader and assets `pdfmp-wasmJs-plugin/.../WasmAssetPlugin.kt`.

**Sample app**: UI in `sample-shared/src/commonMain/.../Sample.kt` (drawer of demos); per-platform bits in `sample-shared/src/*Main/.../PlatformDemos.*.kt` and `Scrollbar.*.kt`.

## Build and test

- Everything is Gradle (`./gradlew`). Version catalog: `gradle/libs.versions.toml`. Shared build setup: `build-logic/`.
- Desktop sample: `./gradlew :desktopapp:run`. Web sample: `./gradlew :webapp:wasmJsBrowserDevelopmentExecutableDistribution` (output in `webapp/build/dist/wasmJs/webApp`, serve it from a directory URL so Compose resource URIs resolve).
- Compile checks: `:pdfmp:compileKotlinJvm`, `:pdfmp:compileKotlinWasmJs`, `:pdfmp:compileKotlinLinuxX64`, `:pdfmp:compileAndroidMain`.
- Tests (TestBalloon, flat `test("…")` cases, no nested suites):
  - `:pdfmp:jvmTest`: `PdfDataTest`, every `PdfData` source end to end through the real native renderer.
  - `:pdfmp-compose:jvmTest`: tile and page geometry (`VisibleTilesTest`, `PdfVisiblePagesTests`, library loading).
  - `:pdfium:linuxX64Test`: pdfium bindings and `ProgressiveDocument`.
  - There are no wasm tests; `wasmJsTest` tasks are skipped.
- **Test tasks ignore failures** (`build-logic/.../TestConfiguration.kt` sets `ignoreFailures`). A green build does not mean green tests: read `build/test-results/<task>/*.xml`.
- If a moved or new TestBalloon suite is not picked up, rerun the test compilation with `--rerun`.
- KSP and the KNI plugin generate code into `build/generated/ksp/...`: look there to see the JNI glue or worker proxies.

## Conventions and gotchas

- Keep user-facing API in `consumerMain` and `@InternalPdfmpApi` for plumbing. pdfmp and pdfmp-compose opt in module-wide.
- Platform logic goes in `expect`/`actual` (or per-platform files); shared logic belongs in the common or `pdfium`/`pdfiumMain` source sets (the progressive loop and `ProgressiveChunks` are examples).
- Native calls from cancelled coroutines keep running. Buffers handed to native code must not be closed or reused until the render ends: `PdfState.renderToCompletion` and `ImageBufferPool.withRendering` exist for that.
- Wasm memory (pdfium's and Skia's) never shrinks, so free buffers promptly on the web.
- JVM `ImageBuffer` caches a Skia `Image` per buffer; `reset()` marks it stale after a render.
- Web `PdfData.url` needs a CORS-enabled server. Compose resource URIs are relative to the page URL.
- Comments in this repo are short; match that.

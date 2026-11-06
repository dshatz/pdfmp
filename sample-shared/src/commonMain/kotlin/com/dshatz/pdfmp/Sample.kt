package com.dshatz.pdfmp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GeneratingTokens
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshatz.pdfmp.compose.PdfView
import com.dshatz.pdfmp.compose.state.DisplayState
import com.dshatz.pdfmp.compose.state.rememberPdfState
import com.dshatz.pdfmp.compose.state.zoomPercents
import com.dshatz.pdfmp.sample.Res
import com.dshatz.pdfmp.sample.ic_doc
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** A demo in the drawer. [description] says how its document is loaded. */
class Demo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

private class DemoSection(val title: String, val demos: List<Demo>)

internal const val SAMPLE = "files/sample.pdf"

private fun demoSections() = listOf(
    DemoSection(
        "Documents", listOf(
            Demo("Full document", "Zoom and scroll controls", Icons.Default.Description) { FullDoc() },
            Demo("Pages 3–4", "pageRange = 2..3, with padding", Icons.Default.ContentCut) {
                DocumentView(PdfData.uri(Res.getUri(SAMPLE)), pageRange = 2..3, contentPadding = PaddingValues(vertical = 40.dp))
            },
            Demo("Landscape", "Wide pages", Icons.Default.Panorama) {
                DocumentView(PdfData.uri(Res.getUri("files/landscape.pdf")))
            },
            Demo("Colorful", "Images and colors", Icons.Default.Palette) {
                DocumentView(PdfData.uri(Res.getUri("files/sample3.pdf")))
            },
        )
    ),
    DemoSection(
        "Sources", listOf(
            Demo("Resource URI", "uri(Res.getUri(…)), read in place", Icons.Default.Link) {
                DocumentView(PdfData.uri(Res.getUri(SAMPLE)))
            },
            Demo("Bytes", "bytes(Res.readBytes(…))", Icons.Default.Memory) {
                val data by produceState<PdfData?>(null) { value = PdfData.bytes(Res.readBytes(SAMPLE)) }
                DocumentView(data)
            },
            Demo("File", "file(…), resource copied to disk", Icons.Default.InsertDriveFile) {
                FileDemo()
            },
            Demo("URL", "url(…), range requests", Icons.Default.Language) {
                DocumentView(PdfData.url("https://www.princexml.com/samples/icelandic/dictionary.pdf"))
            },
            Demo("Missing file", "Error state", Icons.Default.ErrorOutline) {
                DocumentView(PdfData.file(Path("missing.pdf")))
            },
            Demo("Open a file", "Picked with FileKit", Icons.Default.FolderOpen) { OpenFileDemo() },
        )
    ),
    DemoSection("Platform", platformDemos()),
    DemoSection("Dynamic", listOf(
        Demo("Generated", "Dynamically generated document", Icons.Default.GeneratingTokens) {
            Column {
                var text by remember { mutableStateOf("Hello, dynamic PDF!\n\nType here to regenerate the document.") }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("PDF Content") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val data by produceState<PdfData?>(null, text) {
                    delay(300.milliseconds) // debounce typing
                    value = PdfData.bytes(withContext(Dispatchers.Default) { SimplePdfWriter.generate(text) })
                }
                Box(Modifier.weight(1f)) { DocumentView(data) }
            }
        }
    ))
).filter { it.demos.isNotEmpty() }

/** Demos of the sources specific to this platform, e.g. its file picker. */
internal expect fun platformDemos(): List<Demo>

@Composable
fun Sample() {
    SideEffect {
        PDFMP.enableLogs = true
    }
    MaterialTheme {
        val sections = remember { demoSections() }
        var selected by remember { mutableStateOf(sections.first().demos.first()) }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Gray).systemBarsPadding()) {
            val wide = maxWidth > 700.dp
            var expanded by remember { mutableStateOf(wide) }
            Row(Modifier.fillMaxSize()) {
                Drawer(
                    sections = sections,
                    selected = selected,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onSelect = {
                        selected = it
                        // On narrow screens the drawer takes too much room once a demo is chosen.
                        if (!wide) expanded = false
                    },
                )
                AnimatedContent(selected, modifier = Modifier.weight(1f).fillMaxHeight()) { demo ->
                    Box(Modifier.fillMaxSize()) { demo.content() }
                }
            }
        }
    }
}

@Composable
private fun Drawer(
    sections: List<DemoSection>,
    selected: Demo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (Demo) -> Unit,
) {
    val width by animateDpAsState(if (expanded) 260.dp else 64.dp)
    Surface(Modifier.width(width).fillMaxHeight(), tonalElevation = 2.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            IconButton(onClick = onToggle, modifier = Modifier.padding(start = 8.dp)) {
                Icon(if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu, "Toggle menu")
            }
            for (section in sections) {
                AnimatedVisibility(expanded) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                if (!expanded) Spacer(Modifier.padding(top = 12.dp))
                for (demo in section.demos) {
                    DrawerItem(demo, demo == selected, expanded) { onSelect(demo) }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(demo: Demo, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(demo.icon, demo.title, modifier = Modifier.size(24.dp))
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp)) {
                Text(demo.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    demo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** [data] with a loading indicator, its error if it can't be opened, and the current page. Null while loading. */
@Composable
internal fun DocumentView(
    data: PdfData?,
    pageRange: IntRange = 0..Int.MAX_VALUE,
    contentPadding: PaddingValues = PaddingValues.Zero,
) {
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pdf = rememberPdfState(data, pageRange, pageSpacing = 50.dp)
    val state by pdf.displayState
    val layout by pdf.layoutInfo()
    Box(Modifier.fillMaxSize()) {
        PdfView(pdf, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding)
        when (val current = state) {
            is DisplayState.Initializing -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is DisplayState.Error -> Text(
                current.error.message.orEmpty(),
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            else -> layout?.let {
                Scrollbar(it, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                ElevatedCard(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
                    val pageIdx = it.mostVisiblePage.value?.pageIdx ?: 0
                    Text("Page ${pageIdx + 1} / ${it.totalPages.value}", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

/** A document picked with FileKit's picker. */
@Composable
private fun OpenFileDemo() {
    var picked by remember { mutableStateOf<PdfData?>(null) }
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(setOf("pdf"))) { file ->
        if (file != null) picked = file.toPdfData()
    }
    PickedDocument(picked) { launcher.launch() }
}

/** The [PdfData] reading this picked file in place: a path, a content URI or a browser file. */
internal expect fun PlatformFile.toPdfData(): PdfData

/** A button opening a file picker until a document is picked, then the document. */
@Composable
private fun PickedDocument(picked: PdfData?, openPicker: () -> Unit) {
    if (picked == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = openPicker) { Text("Choose a PDF") }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            DocumentView(picked)
            Button(onClick = openPicker, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Text("Choose another")
            }
        }
    }
}

/** The sample resource written to a temporary file, then opened as a file. */
@Composable
private fun FileDemo() {
    val data by produceState<Result<PdfData>?>(null) {
        value = runCatching {
            val bytes = Res.readBytes(SAMPLE)
            withContext(Dispatchers.Default) {
                val path = Path(SystemTemporaryDirectory, "pdfmp-sample.pdf")
                SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
                PdfData.file(path)
            }
        }
    }
    val result = data
    when {
        result == null -> DocumentView(null)
        result.isSuccess -> DocumentView(result.getOrThrow())
        else -> Text(
            "Files are not available here: ${result.exceptionOrNull()?.message}",
            color = Color.Red,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun FullDoc() {
    val pdf = rememberPdfState(PdfData.uri(Res.getUri(SAMPLE)), pageSpacing = 100.dp)

    // This is how to observe current document state.
    val zoom by pdf.zoomPercents()
    val layoutInfo by pdf.layoutInfo()

    Box {
        PdfView(
            pdf,
            modifier = Modifier.fillMaxSize()
        )

        val scope = rememberCoroutineScope()

        layoutInfo?.let { layoutInfo ->
            Scrollbar(layoutInfo, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            ElevatedCard(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            ) {
                val mostVisiblePageIdx by derivedStateOf {
                    layoutInfo.mostVisiblePage.value?.pageIdx ?: 0
                }
                Text("Page ${mostVisiblePageIdx + 1} / ${layoutInfo.totalPages.value} (Zoom $zoom%)", fontSize = 24.sp, modifier = Modifier.padding(5.dp))
                Text("Vertical scroll ${layoutInfo.offsetY.roundToInt()}/${layoutInfo.documentHeight.value.roundToInt()}", fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    OutlinedIconButton(
                        onClick = {
                            scope.launch {
                                layoutInfo.animateScrollTo(layoutInfo.pageRange.value.first)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowUpward, null)
                    }

                    OutlinedIconButton(
                        onClick = {
                            scope.launch {
                                layoutInfo.animateScrollTo(layoutInfo.pageRange.value.last)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowDownward, null)
                    }
                }
            }
            Column(modifier = Modifier.padding(20.dp).align(Alignment.BottomEnd)) {
                val zoom by layoutInfo.zoom
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            layoutInfo.animateSetZoom(zoom + 0.2f)
                        }
                    }
                ) {
                    Icon(Icons.Default.ZoomIn, null)
                }

                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            layoutInfo.animateSetZoom(zoom - 0.2f)
                        }
                    }
                ) {
                    Icon(Icons.Default.ZoomOut, null)
                }

                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            layoutInfo.animateSetZoom(1f)
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Clear, null)
                }
            }
        }
    }
}

@Composable
fun iconPainter(): Painter = painterResource(Res.drawable.ic_doc)

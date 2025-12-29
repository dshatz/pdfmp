package com.dshatz.pdfmp

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshatz.pdfmp.compose.PdfView
import com.dshatz.pdfmp.compose.source.asyncPdfResource
import com.dshatz.pdfmp.compose.state.DisplayState
import com.dshatz.pdfmp.compose.state.rememberPdfState
import com.dshatz.pdfmp.compose.state.zoomPercents
import com.dshatz.pdfmp.sample.Res
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlin.math.roundToInt

@Composable
fun Sample() {
    Column(Modifier.fillMaxSize().background(Color.Gray)) {
        var selected by remember { mutableStateOf(1) }
        val truncatedRange = 2..3
        PrimaryTabRow(
            selected,
            modifier = Modifier.height(60.dp)
        ) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                content = {
                    Text("Full document")
                }
            )

            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                content = {
                    Text("Pages $truncatedRange")
                }
            )

            Tab(
                selected = selected == 2,
                onClick = { selected = 2 },
                content = {
                    Text("Landscape")
                }
            )

            Tab(
                selected = selected == 3,
                onClick = { selected = 3 },
                content = {
                    Text("Colorful")
                }
            )

            Tab(
                selected = selected == 4,
                onClick = { selected = 4 },
                content = {
                    Text("Missing file")
                }
            )
        }
        AnimatedContent(selected, modifier = Modifier.weight(1f)) {
            if (it == 0) {
                FullDoc()
            } else if (it == 1) {
                TruncatedDoc(truncatedRange)
            } else if (it == 2) {
                LandscapeDoc()
            } else if (it == 3) {
                ColoredDoc()
            } else {
                MissingDoc()
            }
        }

    }
}

@Composable
private fun FullDoc() {
    val res by asyncPdfResource {
        Res.readBytes("files/sample.pdf")
    }

    res?.let {
        val pdf = rememberPdfState(it, pageSpacing = 100.dp)

        // This is how to observe current document state.
        val zoom by pdf.zoomPercents()
        val scrollState by pdf.layoutInfo()

        Box {
            PdfView(
                pdf,
                modifier = Modifier.fillMaxSize()
            )

            val scope = rememberCoroutineScope()

            scrollState?.let { scrollState ->
                ElevatedCard(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                ) {
                    val mostVisiblePageIdx by derivedStateOf {
                        scrollState.mostVisiblePage.value?.pageIdx ?: 0
                    }
                    Text("Page ${mostVisiblePageIdx + 1} / ${scrollState.totalPages.value} (Zoom $zoom%)", fontSize = 24.sp, modifier = Modifier.padding(5.dp))
                    Text("Vertical scroll ${scrollState.offsetY.roundToInt()}/${scrollState.documentHeight.value.roundToInt()}", fontSize = 20.sp)
                    Column {
                        OutlinedIconButton(
                            onClick = {
                                scope.launch {
                                    scrollState.animateScrollTo(scrollState.pageRange.value.first)
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowUpward, null)
                        }

                        OutlinedIconButton(
                            onClick = {
                                scope.launch {
                                    scrollState.animateScrollTo(scrollState.pageRange.value.last)
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowDownward, null)
                        }
                    }
                }
                Column(modifier = Modifier.padding(20.dp).align(Alignment.BottomEnd)) {
                    val zoom by scrollState.zoom
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                scrollState.animateSetZoom(zoom + 0.2f)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ZoomIn, null)
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                scrollState.animateSetZoom(zoom - 0.2f)
                            }
                        }
                    ) {
                        Icon(Icons.Default.ZoomOut, null)
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                scrollState.animateSetZoom(1f)
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Clear, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun TruncatedDoc(range: IntRange) {
    val res by asyncPdfResource {
        Res.readBytes("files/sample.pdf")
    }

    res?.let {
        val pdf = rememberPdfState(it, range)
        PdfView(
            pdf,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LandscapeDoc() {
    val res by asyncPdfResource {
        Res.readBytes("files/landscape.pdf")
    }

    res?.let {
        val pdf = rememberPdfState(it)
        PdfView(
            pdf,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ColoredDoc() {
    val res by asyncPdfResource {
        Res.readBytes("files/sample3.pdf")
    }

    res?.let {
        val pdf = rememberPdfState(it)
        PdfView(
            pdf,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MissingDoc() {
    val pdf = rememberPdfState(PdfSource.PdfPath(Path("missing.pdf")))
    val state by pdf.displayState
    Box {
        PdfView(
            pdf,
            modifier = Modifier.fillMaxSize()
        )
        (state as? DisplayState.Error)?.error?.let {
            Text(it.message.orEmpty(), modifier = Modifier.align(Alignment.Center))
        }
    }
}
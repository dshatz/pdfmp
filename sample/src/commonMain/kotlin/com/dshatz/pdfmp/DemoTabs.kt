package com.dshatz.pdfmp

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dshatz.pdfmp.compose.PdfPage
import com.dshatz.pdfmp.compose.PdfPageColumn
import com.dshatz.pdfmp.source.PdfSource
import kotlinx.io.files.Path


@Composable
internal fun DemoTabs(source: PdfSource) {
    var page by remember { mutableStateOf(0) }
    val path by remember { mutableStateOf(source) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(page, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = page == 0,
                text = {
                    Text("One page")
                },
                onClick = { page = 0 }
            )
            Tab(
                selected = page == 1,
                text = {
                    Text("All pages")
                },
                onClick = { page = 1 }
            )
        }
        AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (it) {
                0 -> PdfPage(path, modifier = Modifier.fillMaxSize())
                1 -> PdfPageColumn(path, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
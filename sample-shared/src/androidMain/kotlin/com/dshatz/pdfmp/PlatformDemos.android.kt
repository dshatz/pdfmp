package com.dshatz.pdfmp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.dshatz.pdfmp.sample.Res
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.File

internal actual fun platformDemos(): List<Demo> = listOf(
    Demo("Content URI", "content:// from a FileProvider", Icons.Default.Share) {
        val context = LocalContext.current
        val data by produceState<PdfData?>(null) {
            val bytes = Res.readBytes(SAMPLE)
            value = withContext(Dispatchers.IO) {
                val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "sample.pdf")
                file.writeBytes(bytes)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.pdfmp.files", file)
                PdfData.uri(uri.toString())
            }
        }
        DocumentView(data)
    },
)

internal actual fun PlatformFile.toPdfData(): PdfData = when (val picked = androidFile) {
    is AndroidFile.UriWrapper -> PdfData.uri(picked.uri.toString())
    is AndroidFile.FileWrapper -> PdfData.file(Path(picked.file.absolutePath))
}

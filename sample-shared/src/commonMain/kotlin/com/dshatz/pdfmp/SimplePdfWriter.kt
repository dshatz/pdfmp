package com.dshatz.pdfmp

/** Builds a minimal text-only PDF (Helvetica, A4, Latin-1) without any library. */
internal object SimplePdfWriter {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 56
    private const val FONT = 12
    private const val LEADING = 16
    private const val CHARS_PER_LINE = 80

    fun generate(text: String, minPages: Int = 1): ByteArray {
        val lines = wrap(text)
        val perPage = (H - 2 * MARGIN) / LEADING
        val pages = lines.chunked(perPage).ifEmpty { listOf(emptyList()) }.toMutableList()
        while (pages.size < minPages) pages += emptyList<String>()

        // Objects: 1 catalog, 2 pages, 3 font, then (page, content) pairs.
        val objects = ArrayList<String>()
        val kids = pages.indices.joinToString(" ") { "${4 + it * 2} 0 R" }
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        pages.forEachIndexed { i, pageLines ->
            val stream = buildString {
                append("BT /F1 $FONT Tf $LEADING TL $MARGIN ${H - MARGIN} Td\n")
                pageLines.forEach { append("(${escape(it)}) '\n") }
                append("ET\n")
                append("BT /F1 9 Tf ${W / 2 - 20} 24 Td (Page ${i + 1} of ${pages.size}) Tj ET\n")
            }
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $W $H] " +
                "/Resources << /Font << /F1 3 0 R >> >> /Contents ${5 + i * 2} 0 R >>"
            objects += "<< /Length ${stream.length} >>\nstream\n$stream" + "endstream"
        }

        val out = StringBuilder("%PDF-1.4\n")
        val offsets = ArrayList<Int>()
        objects.forEachIndexed { i, body ->
            offsets += out.length
            out.append("${i + 1} 0 obj\n$body\nendobj\n")
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        // Latin-1: one char per byte, so offsets above are byte offsets.
        return ByteArray(out.length) { out[it].code.coerceAtMost(255).toByte() }
    }

    private fun escape(s: String) = buildString {
        for (c in s) when {
            c == '\\' || c == '(' || c == ')' -> append('\\').append(c)
            c.code in 32..126 -> append(c)
            else -> append('?')
        }
    }

    private fun wrap(text: String): List<String> = text.split('\n').flatMap { para ->
        if (para.isEmpty()) return@flatMap listOf("")
        val result = ArrayList<String>()
        var line = ""
        for (word in para.split(' ')) {
            var w = word
            while (w.length > CHARS_PER_LINE) {
                if (line.isNotEmpty()) { result += line; line = "" }
                result += w.take(CHARS_PER_LINE); w = w.drop(CHARS_PER_LINE)
            }
            line = when {
                line.isEmpty() -> w
                line.length + 1 + w.length <= CHARS_PER_LINE -> "$line $w"
                else -> { result += line; w }
            }
        }
        result += line
        result
    }
}

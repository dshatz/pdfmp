package com.dshatz.pdfmp.imagebuffer

/**
 * Debug counters for ImageBuffers living in the Skiko heap.
 * The wasm heap never shrinks, so its size alone can't tell a leak from fragmentation; these counts can.
 */
internal object ImageBufferStats {
    /** Logs every allocation and close. Console output is slow, so keep this off unless hunting leaks. */
    private const val LOG = false

    private var live = 0
    private var liveBytes = 0L

    fun onAllocate(bytes: Int) {
        live++
        liveBytes += bytes
        log("allocated")
    }

    fun onClose(bytes: Int) {
        live--
        liveBytes -= bytes
        log("closed")
    }

    private fun log(event: String) {
        if (!LOG) return
        println("[ImageBufferStats] $event: live = $live (${liveBytes / (1024 * 1024)} MB)")
    }
}

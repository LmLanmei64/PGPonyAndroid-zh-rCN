// ProgressStreams.kt
// PGPony Android — 4.0.4
//
// Byte-counting stream wrappers for the streaming Encrypt/Decrypt file
// paths.
//
// Once files stopped being read into memory there was no ceiling on how
// big they could be, and a 105 MB file is genuinely slow: ZLIB deflate,
// AES, and the SEIPD integrity hash all run over every byte, on a phone,
// single-threaded. Without a byte count the UI shows the same
// indeterminate spinner whether the operation is moving at 3 MB/s or has
// died, and the two are indistinguishable to the user — which is exactly
// how "starts encrypting but never finishes" gets reported.
//
// So: count bytes as they pass, report them upward (rate-limited, since
// a callback per 64 KiB chunk would be ~1700 state updates for a 105 MB
// file), and give the read loop a way to notice cancellation.
//
// ── On cancellation ──────────────────────────────────────────────────
//
// Cancelling the coroutine is not enough on its own. The crypto call is
// an ordinary blocking loop inside withContext(Dispatchers.IO); it never
// suspends, so it never reaches a cancellation point and keeps running
// to completion on a background thread no matter what the Job says.
// Checking the flag here, on every chunk, is what actually stops it.

package com.pgpony.android.ui.util

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InterruptedIOException

/**
 * Wraps [delegate], reporting cumulative bytes read to [onProgress] and
 * aborting with [InterruptedIOException] once [isCancelled] returns true.
 *
 * [onProgress] fires at most once per [reportEvery] bytes, plus once at
 * end of stream, so a caller can push it straight into UI state.
 */
class ProgressInputStream(
    delegate: InputStream,
    private val reportEvery: Long = 512L * 1024,
    private val isCancelled: () -> Boolean = { false },
    private val onProgress: (Long) -> Unit,
) : FilterInputStream(delegate) {

    private var total = 0L
    private var lastReported = 0L

    private fun advance(n: Int) {
        if (n <= 0) return
        total += n
        if (total - lastReported >= reportEvery) {
            lastReported = total
            onProgress(total)
        }
    }

    private fun checkCancelled() {
        if (isCancelled()) {
            throw InterruptedIOException("Operation cancelled")
        }
    }

    override fun read(): Int {
        checkCancelled()
        val b = `in`.read()
        if (b >= 0) advance(1) else finish()
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkCancelled()
        val n = `in`.read(b, off, len)
        if (n > 0) advance(n) else finish()
        return n
    }

    /** Emit the final count so the UI lands on 100% rather than 99%. */
    private fun finish() {
        if (total != lastReported) {
            lastReported = total
            onProgress(total)
        }
    }
}

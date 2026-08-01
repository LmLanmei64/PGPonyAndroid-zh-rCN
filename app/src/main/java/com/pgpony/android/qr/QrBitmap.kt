// QrBitmap.kt
// PGPony Android — 4.1.0 Phase 9 (issue #3)
//
// The one place a QR bitmap is produced.
//
// This code existed twice: KeyDetailViewModel.encodeQR and the inline block in
// ExchangeViewModel.generateQR were the same matrix-to-bitmap loop, and
// encodeQR's own comment promised the two were "kept in sync" by hand. Phase 7
// had already shown what that costs, when a one-line fix to the envelope
// unwrap had to be applied to two byte-identical private functions or they
// would silently diverge. Same shape, so the same answer.
//
// Everything Android-specific lives here; the format itself is in QrChunking,
// which is pure Kotlin and unit-tested.

package com.pgpony.android.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrBitmap {

    private const val SIZE = 800

    /**
     * Encode [text] as one or more QR bitmaps.
     *
     * One bitmap for anything that fits a single symbol, which is every
     * classic key and what 4.0.x always produced. Several for a key that does
     * not, each carrying a `PGPONY1:` frame header.
     *
     * Returns **null** when even chunking cannot hold it, which is the
     * caller's cue to show `R.string.qr_too_large` rather than let a
     * `WriterException` reach the user as "QR generation failed: data too
     * big". That message is what issue #3 was actually reported as.
     */
    fun encodeFrames(text: String): List<Bitmap>? =
        QrChunking.split(text)?.map { encodeOne(it) }

    /** One symbol. Throws ZXing's WriterException if even this will not fit. */
    fun encodeOne(text: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )
            }
        }
        return bitmap
    }
}

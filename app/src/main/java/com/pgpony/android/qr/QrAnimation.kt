// QrAnimation.kt
// PGPony Android — 4.3.0 RC3 §5.6.5 (#37, CertainBot)
//
// One source of truth for the animated multi-part QR cadence, shared by the
// Key Detail QR sheet and the Exchange tab so the two rotate in lockstep.

package com.pgpony.android.qr

object QrAnimation {
    // Per-frame interval. RC4: halved from the 1000ms provisional to 500ms
    // on CertainBot's RC3 scanner test (3 Android devices, per-tile read
    // under 1/4 of the old window, i.e. <250ms, so 500ms keeps a 2x margin).
    // The manual next/prev buttons stay. Remaining check before 4.3.0 final:
    // an iOS device scanning an Android animated QR at this rate, the one
    // path CertainBot's Android-only test did not cover (§5.6.5 gate).
    const val FRAME_INTERVAL_MS = 500L
}

// CardPinCache.kt
// PGPony Android — 3.1.0 Phase 7 (B1/B2)
//
// In-memory cache for the card USER PIN (PW1), so it isn't re-typed on
// every operation. Port of iOS 7.1.x CardPINCache. Properties:
//
//   • Memory only — the PIN never touches SharedPreferences, disk, or
//     logs. Killing the app clears it by construction.
//   • The user-chosen DURATION is the boundary (B2), not lifecycle
//     events: expiry is capturedAt + duration, checked on every read.
//   • Duration changes apply to an already-held PIN immediately (the
//     7.1.x F-item): expiry is recomputed from the CURRENT preference
//     on every read, so shortening the duration can expire a held PIN
//     on the spot and lengthening extends it.
//   • Wrong PIN clears the cache (wired at OpenPgpCardSession.verify,
//     the single chokepoint every operation passes through).
//   • Only PW1 (0x81 sign / 0x82 decrypt) is ever cached. The admin
//     PIN (PW3) is never cached and admin prompts are never prefilled.
//
// The enable flag (default OFF) and duration live in the app prefs;
// the PIN itself lives only in this object's field.

package com.pgpony.android.crypto.card

import android.content.Context
import com.pgpony.android.PGPonyApp

object CardPinCache {

    private const val PREFS = "pgpony_prefs"
    const val KEY_ENABLED = "card_pin_cache_enabled"
    const val KEY_DURATION_SEC = "card_pin_cache_duration_sec"
    const val DEFAULT_DURATION_SEC = 300 // 5 minutes

    @Volatile private var pin: String? = null
    @Volatile private var capturedAt: Long = 0L

    private fun prefs() =
        PGPonyApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs().getBoolean(KEY_ENABLED, false)

    fun durationSec(): Int = prefs().getInt(KEY_DURATION_SEC, DEFAULT_DURATION_SEC)

    /** Remember a successfully-verified PW1. No-op when disabled. */
    fun remember(pinValue: String) {
        if (!isEnabled()) return
        pin = pinValue
        capturedAt = System.currentTimeMillis()
    }

    /**
     * The cached PIN, or null when disabled, empty, or expired. Expiry
     * is evaluated against the CURRENT duration preference (recompute-
     * on-read — see header).
     */
    fun retrieve(): String? {
        if (!isEnabled()) return null
        val held = pin ?: return null
        if (remainingMs() <= 0) {
            clear()
            return null
        }
        return held
    }

    /** Milliseconds until the held PIN expires; 0 when none is held. */
    fun remainingMs(): Long {
        if (pin == null) return 0L
        val expiresAt = capturedAt + durationSec() * 1000L
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isHolding(): Boolean = pin != null && remainingMs() > 0

    /** Drop the held PIN (wrong PIN, user request, toggle off). */
    fun clear() {
        pin = null
        capturedAt = 0L
    }

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clear()
    }

    fun setDurationSec(seconds: Int) {
        // No clamp-to-old-expiry bookkeeping needed: retrieve() and
        // remainingMs() read the preference live (B3's countdown updates
        // on the next tick, and a held PIN honors the new duration
        // immediately).
        prefs().edit().putInt(KEY_DURATION_SEC, seconds).apply()
    }
}

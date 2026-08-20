// ProviderPassphraseCache.kt
// PGPony Android — 4.0.0 Succession Phase P2a-2 (provider send path)
//
// In-process, in-memory passphrase cache for OpenPGP API operations,
// keyed by 64-bit sign-key id. This is the OpenKeychain model: the
// provider's passphrase dialog (ProviderPassphraseActivity) stores the
// entered passphrase here, the client retries its API call, and the
// service picks the passphrase up — the passphrase itself NEVER
// crosses the binder back to the client app.
//
// Properties:
//   • memory only — process death clears it (same posture as the card
//     PIN cache's process-death rule)
//   • RC3 §J (#15): the TTL is now the user's chosen duration, ported
//     from CardPinCache's picker pattern — 1 min / 5 min / 15 min /
//     1 hour / "Until I clear it" (sentinel, no timer). The default
//     stays 5 minutes, which is exactly the fixed TTL this cache has
//     had since P2a-2 — no behavior change until the user picks.
//   • expiry is recomputed from the CURRENT preference on every read
//     (the CardPinCache B2 rule): shortening the duration can expire a
//     held passphrase on the spot, lengthening extends it, and the
//     sentinel takes effect on held entries instantly in both
//     directions.
//   • cleared on wrong-passphrase so a stale entry can't loop
//   • RC3 §J (#15): clearKeys(...) is the passphrase-change
//     invalidation hook — 4.3.0 §1.1 (change key passphrase) calls
//     KeyRepository.invalidateCachedPassphrases, which resolves a
//     ring's key ids and clears them here, so a passphrase that just
//     changed can never be replayed from cache.
//
// PGPony's own generator creates passphrase-less keys by default, so
// most users never hit this path — it exists for imported keys that
// carry a passphrase.

package com.pgpony.android.provider

import android.content.Context
import android.os.SystemClock
import com.pgpony.android.PGPonyApp
import com.pgpony.android.session.SessionPolicy
import java.util.concurrent.ConcurrentHashMap

object ProviderPassphraseCache {

    private const val PREFS = "pgpony_prefs"
    const val KEY_DURATION_SEC = "passphrase_cache_duration_sec"
    const val DEFAULT_DURATION_SEC = 300 // 5 minutes — the pre-#15 fixed TTL

    /** Sentinel duration for "Until I clear it" (same value and
     *  semantics as CardPinCache.DURATION_UNTIL_CLEARED): held entries
     *  have no timer and clear only on wrong passphrase, manual Clear,
     *  invalidation, or process death. */
    const val DURATION_UNTIL_CLEARED = -1

    private data class Entry(val passphrase: String, val storedAt: Long)

    private val entries = ConcurrentHashMap<Long, Entry>()

    // Null when the app context isn't up (pure-JVM unit tests, where
    // PGPonyApp.instance is an uninitialized lateinit). The cache then
    // falls back to the default duration, matching CardPinCache's
    // prefsOrNull posture.
    private fun prefsOrNull() = runCatching {
        PGPonyApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()

    // §3 (#15): unified — reads the single SessionPolicy duration.
    fun durationSec(): Int = SessionPolicy.durationSec()

    fun isUntilCleared(): Boolean = SessionPolicy.isUntilCleared()

    fun setDurationSec(seconds: Int) {
        SessionPolicy.setDurationSec(seconds)
    }

    private fun remainingMsOf(entry: Entry): Long {
        if (SessionPolicy.isLifecycleHeld()) return Long.MAX_VALUE
        val expiresAt = entry.storedAt + durationSec() * 1000L
        return (expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    fun put(keyId: Long, passphrase: String) {
        entries[keyId] = Entry(passphrase, SystemClock.elapsedRealtime())
    }

    fun get(keyId: Long): String? {
        val entry = entries[keyId] ?: return null
        if (remainingMsOf(entry) <= 0L) {
            entries.remove(keyId)
            return null
        }
        return entry.passphrase
    }

    /**
     * RC3 §J (#15): milliseconds until the LAST held entry expires
     * (entries are per-key and can expire at different moments); 0 when
     * none held. Under the sentinel, Long.MAX_VALUE — Settings branches
     * on isUntilCleared() for display, same contract as CardPinCache.
     * Expired entries encountered during the sweep are pruned.
     */
    fun remainingMs(): Long {
        var max = 0L
        for ((keyId, entry) in entries) {
            val r = remainingMsOf(entry)
            if (r <= 0L) entries.remove(keyId) else if (r > max) max = r
        }
        return max
    }

    fun isHolding(): Boolean = remainingMs() > 0L

    fun clear(keyId: Long) {
        entries.remove(keyId)
    }

    /** RC3 §J (#15): the invalidation hook — see the file header. */
    fun clearKeys(keyIds: Collection<Long>) {
        keyIds.forEach { entries.remove(it) }
    }

    fun clearAll() {
        entries.clear()
    }
}

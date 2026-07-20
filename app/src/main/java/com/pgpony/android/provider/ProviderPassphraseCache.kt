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
//   • 5-minute TTL from last store, so an abandoned unlock doesn't
//     linger for hours
//   • cleared on wrong-passphrase so a stale entry can't loop
//
// PGPony's own generator creates passphrase-less keys by default, so
// most users never hit this path — it exists for imported keys that
// carry a passphrase.

package com.pgpony.android.provider

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object ProviderPassphraseCache {

    private const val TTL_MS = 5 * 60 * 1000L

    private data class Entry(val passphrase: String, val storedAt: Long)

    private val entries = ConcurrentHashMap<Long, Entry>()

    fun put(keyId: Long, passphrase: String) {
        entries[keyId] = Entry(passphrase, SystemClock.elapsedRealtime())
    }

    fun get(keyId: Long): String? {
        val entry = entries[keyId] ?: return null
        if (SystemClock.elapsedRealtime() - entry.storedAt > TTL_MS) {
            entries.remove(keyId)
            return null
        }
        return entry.passphrase
    }

    fun clear(keyId: Long) {
        entries.remove(keyId)
    }

    fun clearAll() {
        entries.clear()
    }
}

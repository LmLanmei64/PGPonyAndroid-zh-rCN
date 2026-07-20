// AutocryptHeader.kt
// PGPony Android — 4.0.0 Phase 4 (Autocrypt) — app-side header handling
//
// Parse Autocrypt / Autocrypt-Gossip headers off email that PGPony itself
// decrypts (so the standalone app acquires sender keys automatically, the
// same way the provider does for Thunderbird), and build our own
// Autocrypt header for PGP/MIME output.
//
// Autocrypt Level 1 header:
//   Autocrypt: addr=a@b.com; [prefer-encrypt=mutual;] keydata=<base64>
// where keydata is base64 of the binary transferable public key.
// Autocrypt-Gossip omits prefer-encrypt and rides inside the encrypted
// part, one per recipient.

package com.pgpony.android.autocrypt

import com.pgpony.android.data.repository.KeyRepository
import java.util.Base64

/** A parsed Autocrypt (or -Gossip) header. */
data class ParsedAutocrypt(
    val addr: String,
    val keyData: ByteArray,
    val isMutual: Boolean
)

object AutocryptHeader {

    /** Parse one header VALUE (text after "Autocrypt:") → parts, or null. */
    fun parseValue(value: String): ParsedAutocrypt? {
        var addr: String? = null
        var keydata: String? = null
        var prefer: String? = null
        // Attributes are ';'-separated key=value; keydata may itself hold
        // '=' padding, so split only on the FIRST '='.
        for (attr in value.split(';')) {
            val a = attr.trim()
            val eq = a.indexOf('=')
            if (eq <= 0) continue
            val key = a.substring(0, eq).trim().lowercase()
            val v = a.substring(eq + 1).trim()
            when (key) {
                "addr" -> addr = v
                "keydata" -> keydata = v
                "prefer-encrypt" -> prefer = v.lowercase()
                // "_" -prefixed critical attrs we don't understand would,
                // per spec, invalidate the header — but Level 1 defines none
                // beyond these, so unknown non-critical attrs are ignored.
            }
        }
        if (addr.isNullOrBlank() || keydata.isNullOrBlank()) return null
        val bytes = runCatching {
            Base64.getMimeDecoder().decode(keydata.replace(Regex("\\s"), ""))
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return ParsedAutocrypt(addr.lowercase(), bytes, prefer == "mutual")
    }

    /**
     * Extract all values for [name] from an email's header block (before
     * the first blank line), unfolding RFC 5322 continuation lines. Case-
     * insensitive on the header name.
     */
    fun extractHeaders(rawEmail: String, name: String): List<String> {
        val normalized = rawEmail.replace("\r\n", "\n")
        val headerBlock = normalized.substringBefore("\n\n")
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var capturing = false
        val prefix = "$name:"
        for (line in headerBlock.split('\n')) {
            if (line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t')) {
                if (capturing) sb.append(' ').append(line.trim())
                continue
            }
            // a new header line ends any capture in progress
            if (capturing) { out.add(sb.toString().trim()); sb.setLength(0); capturing = false }
            if (line.length >= prefix.length &&
                line.substring(0, prefix.length).equals(prefix, ignoreCase = true)
            ) {
                sb.append(line.substring(prefix.length).trim())
                capturing = true
            }
        }
        if (capturing) out.add(sb.toString().trim())
        return out
    }

    /**
     * The current user's own Autocrypt header, built from their default
     * key, for injection into outgoing PGP/MIME. Null when there's no
     * default key or it has no email. NOTE: only reaches the recipient
     * when the .eml is sent verbatim — most mail apps rebuild the outer
     * headers when you share into them.
     */
    suspend fun currentUserHeader(repo: KeyRepository, mutual: Boolean = false): String? {
        val key = repo.getDefaultKey() ?: return null
        val email = key.userEmail.ifBlank { return null }
        val ring = repo.loadPublicKeyRing(key.fingerprint) ?: return null
        return buildHeader(email, ring.encoded, mutual)
    }

    /** Build our outgoing Autocrypt header line for [addr] + binary key. */
    fun buildHeader(addr: String, publicKeyBinary: ByteArray, mutual: Boolean): String {
        val b64 = Base64.getEncoder().encodeToString(publicKeyBinary)
        // Fold at 76 chars with a leading space per RFC 5322.
        val folded = b64.chunked(76).joinToString("\r\n ")
        val prefer = if (mutual) "prefer-encrypt=mutual; " else ""
        return "Autocrypt: addr=$addr; ${prefer}keydata=\r\n $folded"
    }
}

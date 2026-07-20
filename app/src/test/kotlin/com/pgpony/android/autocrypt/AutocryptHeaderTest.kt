// AutocryptHeaderTest.kt
// PGPony Android — 4.0.0 Phase 4 (Autocrypt) — header parse/build tests

package com.pgpony.android.autocrypt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AutocryptHeaderTest {

    private val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val keyB64 = Base64.getEncoder().encodeToString(key)

    @Test fun parses_addr_keydata_and_mutual() {
        val p = AutocryptHeader.parseValue("addr=Alice@B.com; prefer-encrypt=mutual; keydata=$keyB64")
        assertEquals("alice@b.com", p!!.addr)
        assertTrue(p.isMutual)
        assertArrayEquals(key, p.keyData)
    }

    @Test fun nopreference_when_prefer_absent() {
        val p = AutocryptHeader.parseValue("addr=a@b.com; keydata=$keyB64")
        assertFalse(p!!.isMutual)
    }

    @Test fun null_when_keydata_missing() {
        assertNull(AutocryptHeader.parseValue("addr=a@b.com; prefer-encrypt=mutual"))
    }

    @Test fun tolerates_folded_whitespace_in_keydata() {
        // header value as it arrives after unfolding: keydata split across
        // "lines" collapsed to spaces
        val folded = keyB64.chunked(4).joinToString(" ")
        val p = AutocryptHeader.parseValue("addr=a@b.com; keydata=$folded")
        assertArrayEquals(key, p!!.keyData)
    }

    // ── header extraction from an email block ────────────────────────

    @Test fun extracts_and_unfolds_autocrypt_header() {
        val email = buildString {
            append("From: Alice <alice@b.com>\r\n")
            append("Date: Thu, 17 Jul 2026 10:00:00 +0000\r\n")
            append("Autocrypt: addr=alice@b.com; keydata=abc\r\n")
            append(" def\r\n")   // folded continuation
            append(" ghi\r\n")
            append("Subject: hi\r\n")
            append("\r\n")
            append("body")
        }
        val hs = AutocryptHeader.extractHeaders(email, "Autocrypt")
        assertEquals(1, hs.size)
        assertEquals("addr=alice@b.com; keydata=abc def ghi", hs[0])
        // case-insensitive on name
        assertEquals(1, AutocryptHeader.extractHeaders(email, "autocrypt").size)
    }

    @Test fun extracts_multiple_gossip_headers() {
        val part = buildString {
            append("Content-Type: text/plain\r\n")
            append("Autocrypt-Gossip: addr=b@x.com; keydata=b64b\r\n")
            append("Autocrypt-Gossip: addr=c@x.com; keydata=b64c\r\n")
            append("\r\n")
            append("hi all")
        }
        val g = AutocryptHeader.extractHeaders(part, "Autocrypt-Gossip")
        assertEquals(2, g.size)
        assertTrue(g[0].contains("b@x.com"))
        assertTrue(g[1].contains("c@x.com"))
    }

    @Test fun build_then_parse_round_trips() {
        val header = AutocryptHeader.buildHeader("me@x.com", key, mutual = true)
        // header is "Autocrypt: <value>" — strip the name, unfold, parse
        val value = header.removePrefix("Autocrypt:").replace(Regex("\\r?\\n\\s+"), " ").trim()
        val p = AutocryptHeader.parseValue(value)!!
        assertEquals("me@x.com", p.addr)
        assertTrue(p.isMutual)
        assertArrayEquals(key, p.keyData)
    }
}

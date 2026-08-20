// ZipPackaging.kt
// PGPony Android — 4.3.0 §5.6.3 (#31 zip output)
//
// A thin, streamed .zip packaging layer for encrypt results. The zip is
// transport packaging, NOT encryption: it wraps the already-encrypted
// .gpg/.asc so a store-and-forward channel that mangles those extensions
// still delivers the ciphertext intact. Single entry, streamed both ways
// so a large ciphertext never has to be held in memory (keeps the #32
// class fixed).

package com.pgpony.android.ui.util

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipPackaging {

    /** PGP ciphertext extensions we recognise as the payload inside a zip. */
    private val PGP_EXTENSIONS = listOf(".gpg", ".pgp", ".asc")

    /**
     * Write [body]'s bytes as ONE zip entry named [entryName] into [sink],
     * then close [sink]. Streamed: [body] receives the entry stream and may
     * copy an arbitrary-size source into it. The caller must NOT also close
     * [sink] (this owns it).
     */
    fun writeSingleEntry(sink: OutputStream, entryName: String, body: (OutputStream) -> Unit) {
        ZipOutputStream(sink).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            body(zip)
            zip.closeEntry()
        }
    }

    /**
     * Like [writeSingleEntry] but finishes the archive WITHOUT closing [sink],
     * for callers that own and close the sink themselves (the bundle export
     * streams). The internal deflater is released by GC.
     */
    fun writeSingleEntryNoClose(sink: OutputStream, entryName: String, body: (OutputStream) -> Unit) {
        val zip = ZipOutputStream(sink)
        zip.putNextEntry(ZipEntry(entryName))
        body(zip)
        zip.closeEntry()
        zip.finish()
    }

    /** True if the leading bytes are the local-file-header zip magic (PK). */
    fun looksLikeZip(prefix: ByteArray): Boolean =
        prefix.size >= 4 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4B.toByte() &&
            prefix[2] == 0x03.toByte() && prefix[3] == 0x04.toByte()

    /** A ciphertext entry found inside a zip: its name and whether it is the sole one. */
    data class Entry(val name: String)

    /**
     * Scan [zipStream] (streamed, not fully buffered) and return the names of
     * entries that look like PGP ciphertext, by extension. Directory entries
     * and everything else are ignored. Caller decides single vs bundle from
     * the count. Does NOT close [zipStream].
     */
    fun listPgpEntries(zipStream: ZipInputStream): List<Entry> {
        val found = mutableListOf<Entry>()
        var e: ZipEntry? = zipStream.nextEntry
        while (e != null) {
            val name = e.name
            if (!e.isDirectory && PGP_EXTENSIONS.any { name.lowercase().endsWith(it) }) {
                found.add(Entry(name))
            }
            zipStream.closeEntry()
            e = zipStream.nextEntry
        }
        return found
    }

    /**
     * Open [source] as a zip and stream the FIRST entry whose name equals
     * [entryName] into [out]. Returns true if written. Streamed; closes
     * neither [source] nor [out].
     */
    fun extractEntry(source: InputStream, entryName: String, out: OutputStream): Boolean {
        val zip = ZipInputStream(source)
        var e: ZipEntry? = zip.nextEntry
        while (e != null) {
            if (!e.isDirectory && e.name == entryName) {
                zip.copyTo(out)
                zip.closeEntry()
                return true
            }
            zip.closeEntry()
            e = zip.nextEntry
        }
        return false
    }
}

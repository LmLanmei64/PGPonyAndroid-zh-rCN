// StructuredDecryptionResultScreen.kt
// PGPony Android — 3.1.0 Phase 4 (J1)
//
// The structured result for a decrypted PGP/MIME message: the body on
// top, then one row per attachment (thumbnail for images, type icon
// otherwise, name + type + size), each with Save (SAF) and Share
// (FileProvider). A bottom "Share all" uses ACTION_SEND_MULTIPLE.
//
// Port of iOS Views/Decrypt/StructuredDecryptionResultView.swift
// (7.1.x). Deviation from iOS noted: iOS offers "Save All"; Android's
// SAF has no multi-file create dialog, so "Share all" (→ Files app or
// any target) covers the bulk case and Save stays per-row.
//
// Tapping a row opens the attachment in a viewer app (ACTION_VIEW via
// a cache-dir FileProvider URI with a one-shot read grant — the same
// pattern FileEncryptionResultScreen A7 Fix3 established).

package com.pgpony.android.ui.decrypt

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pgpony.android.MainActivity
import com.pgpony.android.R
import com.pgpony.android.crypto.mime.MimeAttachment
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredDecryptionResultScreen(
    body: String?,
    attachments: List<MimeAttachment>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findStructuredResultMainActivity()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                stringResource(R.string.structured_result_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // ── Body ─────────────────────────────────────────────────
            if (!body.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ── Attachments ──────────────────────────────────────────
            Text(
                stringResource(R.string.structured_result_attachments_format, attachments.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
            attachments.forEach { att ->
                AttachmentRow(
                    attachment = att,
                    onOpen = { openAttachment(context, att) },
                    onSave = {
                        activity?.startDocumentCreator(
                            // octet-stream: SAF appends typed MIMEs'
                            // canonical extensions (Phase 2 Fix1 lesson);
                            // the attachment's own name stays verbatim.
                            mimeType = "application/octet-stream",
                            suggestedName = att.filename
                        ) { uri ->
                            if (uri == null) return@startDocumentCreator
                            try {
                                context.contentResolver.openOutputStream(uri)?.use {
                                    it.write(att.data)
                                }
                            } catch (_: Exception) {
                                // Save failures surface as a missing file;
                                // the sheet stays up for a retry.
                            }
                        }
                    },
                    onShare = { shareAttachments(context, listOf(att)) }
                )
            }

            // ── Share all + Done ─────────────────────────────────────
            if (attachments.size > 1) {
                OutlinedButton(
                    onClick = { shareAttachments(context, attachments) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.structured_result_share_all))
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_button_done))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: MimeAttachment,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val isImage = attachment.contentType.startsWith("image/")
    // Thumbnail: decode once per attachment, downsampled so a large
    // photo doesn't hold a full-size Bitmap for a 44dp row.
    val thumb: Bitmap? = remember(attachment) {
        if (!isImage) null else try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size, bounds)
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 96)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Icon(
                    if (isImage) Icons.Filled.ImageIcon else Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    attachment.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "${attachment.contentType} · ${formatAttachmentSize(attachment.data.size.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = stringResource(R.string.common_button_save),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.IosShare,
                    contentDescription = stringResource(R.string.common_button_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

/** Write attachments to the cache exports dir and return FileProvider URIs. */
private fun exportUris(context: Context, attachments: List<MimeAttachment>): ArrayList<android.net.Uri> {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val uris = ArrayList<android.net.Uri>(attachments.size)
    for (att in attachments) {
        val safeName = att.filename.replace('/', '_').ifBlank { "attachment" }
        val outFile = File(exportsDir, safeName)
        outFile.writeBytes(att.data)
        uris.add(
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        )
    }
    return uris
}

private fun openAttachment(context: Context, att: MimeAttachment) {
    try {
        val uri = exportUris(context, listOf(att)).first()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, att.contentType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, att.filename))
    } catch (_: Exception) {
        // No viewer for the type, or export failed — non-fatal; the row's
        // Save/Share remain available.
    }
}

private fun shareAttachments(context: Context, attachments: List<MimeAttachment>) {
    try {
        val uris = exportUris(context, attachments)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = attachments.first().contentType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: Exception) {
        // Share sheet unavailable — non-fatal.
    }
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private tailrec fun Context.findStructuredResultMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findStructuredResultMainActivity()
    else -> null
}

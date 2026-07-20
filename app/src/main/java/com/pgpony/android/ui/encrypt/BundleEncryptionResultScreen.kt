// BundleEncryptionResultScreen.kt
// PGPony Android — 3.1.0 Phase 5 (J4)
//
// The output sheet for an encrypted Bundle. Three formats, mirroring
// iOS MessageEncryptionResultView:
//
//   • Share as Email (.eml) — the armored message wrapped in the RFC
//     3156 multipart/encrypted envelope (MimeBuilder.wrapEncrypted),
//     written to the cache exports dir and shared as message/rfc822.
//     The most interoperable: Thunderbird and desktop clients open it
//     directly, and PGPony's own J2 unwrap reads it back.
//   • Share encrypted .asc — the armored block as a standalone file.
//   • Copy Inline Block — the armored block to the clipboard via
//     ClipboardService (auto-clear countdown per the app convention).
//
// FileProvider one-shot grants per the A7 Fix3 pattern; octet-stream
// is NOT needed here because these are share intents, not SAF creates
// (the Phase 2 Fix1 extension-append issue is a document-creator
// behavior).

package com.pgpony.android.ui.encrypt

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pgpony.android.PGPonyApp
import com.pgpony.android.autocrypt.AutocryptHeader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pgpony.android.MainActivity
import com.pgpony.android.R
import com.pgpony.android.crypto.mime.MimeBuilder
import com.pgpony.android.ui.util.ClipboardService
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleEncryptionResultScreen(state: EncryptUiState, onDismiss: () -> Unit) {
    // 4.0.0 Phase 4 — our own Autocrypt header, injected into the .eml
    // output (best-effort; see MimeBuilder.wrapEncrypted).
    var autocryptHeader by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        autocryptHeader = runCatching {
            AutocryptHeader.currentUserHeader(PGPonyApp.instance.keyRepository)
        }.getOrNull()
    }
    val context = LocalContext.current
    // 3.1.0 Phase 5 Fix1: Save-to-Files needs the SAF document creator,
    // which lives on MainActivity (A10b helper).
    val activity = context.findBundleResultMainActivity()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val armored = state.encryptedBundleArmored ?: return
    val recipients = state.selectedRecipients.size
    val attachmentCount = state.bundleAttachments.size

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                stringResource(R.string.bundle_result_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.bundle_result_subtitle_format, attachmentCount, recipients),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 3.1.0 Phase 5 Fix1 — each format gets Share AND Save. Save
            // goes through the SAF document creator with octet-stream (the
            // Phase 2 Fix1 lesson: typed MIMEs get their canonical
            // extension appended, which would produce message.eml.eml).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { shareBundleFile(context, MimeBuilder.wrapEncrypted(armored, autocryptHeader = autocryptHeader), "message.eml", "message/rfc822") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bundle_result_share_eml))
                }
                OutlinedButton(
                    onClick = {
                        saveBundleFile(activity, context, MimeBuilder.wrapEncrypted(armored, autocryptHeader = autocryptHeader), "message.eml")
                    }
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = stringResource(R.string.bundle_result_save_eml)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        shareBundleFile(
                            context,
                            armored.toByteArray(Charsets.UTF_8),
                            "message.asc",
                            "application/pgp-encrypted"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bundle_result_share_asc))
                }
                OutlinedButton(
                    onClick = {
                        saveBundleFile(activity, context, armored.toByteArray(Charsets.UTF_8), "message.asc")
                    }
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = stringResource(R.string.bundle_result_save_asc)
                    )
                }
            }
            OutlinedButton(
                onClick = { ClipboardService.copyText(context, armored) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bundle_result_copy_inline))
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_button_done))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// 3.1.0 Phase 5 Fix1 — SAF save: octet-stream so the suggested name is
// kept verbatim (see Phase 2 Fix1).
private fun saveBundleFile(
    activity: MainActivity?,
    context: Context,
    bytes: ByteArray,
    suggestedName: String
) {
    activity?.startDocumentCreator(
        mimeType = "application/octet-stream",
        suggestedName = suggestedName
    ) { uri ->
        if (uri == null) return@startDocumentCreator
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        } catch (_: Exception) {
            // Write failure surfaces as a missing file; the sheet stays
            // up for a retry.
        }
    }
}

private tailrec fun Context.findBundleResultMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findBundleResultMainActivity()
    else -> null
}

private fun shareBundleFile(context: Context, bytes: ByteArray, name: String, mime: String) {
    try {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportsDir, name)
        outFile.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: Exception) {
        // Share sheet unavailable — non-fatal; the other outputs remain.
    }
}

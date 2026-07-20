// LicensesScreen.kt
// PGPony Android — 4.0.0 Phase 9b
//
// Open-source attributions, reachable from Settings → About → Licenses.
// iOS parity (LicensesView) plus a compliance fix that stands on its
// own: Bouncy Castle ships under an MIT-style license whose terms
// require the copyright notice and permission text to accompany the
// software, and pre-9b builds carried no attribution screen at all.
// ZXing, Ktor, Compose Reorderable, and the AndroidX / Kotlin stack are
// Apache-2.0 and get the standard notice.
//
// License bodies are deliberately hardcoded (not string resources):
// legal text is never translated — the iOS LicensesView hardcodes its
// blurbs the same way — and keeping them out of strings.xml keeps them
// out of the translator pipeline and the untranslated-key counts.
//
// Presented as a full-height ModalBottomSheet, same pattern as
// SecurityInfoScreen / LanguagePickerScreen overlays in this package.

package com.pgpony.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pgpony.android.R

// ── License texts (never localized) ────────────────────────────────────

private const val BOUNCY_CASTLE_LICENSE =
    "Copyright (c) 2000-2025 The Legion of the Bouncy Castle Inc. " +
    "(https://www.bouncycastle.org)\n\n" +
    "Permission is hereby granted, free of charge, to any person obtaining " +
    "a copy of this software and associated documentation files (the " +
    "\"Software\"), to deal in the Software without restriction, including " +
    "without limitation the rights to use, copy, modify, merge, publish, " +
    "distribute, sublicense, and/or sell copies of the Software, and to " +
    "permit persons to whom the Software is furnished to do so, subject to " +
    "the following conditions:\n\n" +
    "The above copyright notice and this permission notice shall be " +
    "included in all copies or substantial portions of the Software.\n\n" +
    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, " +
    "EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF " +
    "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND " +
    "NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS " +
    "BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN " +
    "ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN " +
    "CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE " +
    "SOFTWARE."

private const val APACHE_NOTICE =
    "Licensed under the Apache License, Version 2.0. You may obtain a " +
    "copy of the License at https://www.apache.org/licenses/LICENSE-2.0"

private const val ZXING_LICENSE =
    "Copyright ZXing authors.\n\n$APACHE_NOTICE"

private const val KTOR_LICENSE =
    "Copyright 2000-2025 JetBrains s.r.o. and contributors.\n\n$APACHE_NOTICE"

private const val REORDERABLE_LICENSE =
    "Copyright Calvin Liang (sh.calvin.reorderable).\n\n$APACHE_NOTICE"

private const val ANDROIDX_LICENSE =
    "AndroidX, Jetpack Compose, Material Components, CameraX, and the " +
    "Kotlin standard library and kotlinx libraries: Copyright The Android " +
    "Open Source Project, Google LLC, and JetBrains s.r.o.\n\n$APACHE_NOTICE"

private const val PGPONY_COPYRIGHT =
    "Copyright (c) 2026 Kevin Stewart.\nAll rights reserved."

// ── Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                stringResource(R.string.licenses_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            LicenseSection(name = "Bouncy Castle", body = BOUNCY_CASTLE_LICENSE)
            LicenseSection(name = "ZXing", body = ZXING_LICENSE)
            LicenseSection(name = "Ktor", body = KTOR_LICENSE)
            LicenseSection(name = "Compose Reorderable", body = REORDERABLE_LICENSE)
            LicenseSection(name = "AndroidX, Jetpack & Kotlin", body = ANDROIDX_LICENSE)
            LicenseSection(name = "PGPony", body = PGPONY_COPYRIGHT, isLast = true)
        }
    }
}

@Composable
private fun LicenseSection(name: String, body: String, isLast: Boolean = false) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

// ProviderPassphraseActivity.kt
// PGPony Android — 4.0.0 Succession Phase P2a-2 (provider send path)
//
// Passphrase prompt for provider operations on a protected signing
// key. Launched only via the PendingIntent the service attaches when a
// sign/encrypt call throws PassphraseRequired / InvalidPassphrase (NOT
// exported). On confirm the passphrase goes into
// ProviderPassphraseCache (in-process only — never back across the
// binder), the activity returns RESULT_OK, and the client retries its
// call, which now finds the cached passphrase.

package com.pgpony.android.provider

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pgpony.android.PGPonyTheme
import com.pgpony.android.R

class ProviderPassphraseActivity : ComponentActivity() {

    companion object {
        const val EXTRA_KEY_ID = "com.pgpony.android.provider.PASSPHRASE_KEY_ID"
        const val EXTRA_KEY_LABEL = "com.pgpony.android.provider.PASSPHRASE_KEY_LABEL"
        const val EXTRA_WRONG = "com.pgpony.android.provider.PASSPHRASE_WRONG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyId = intent.getLongExtra(EXTRA_KEY_ID, 0L)
        val keyLabel = intent.getStringExtra(EXTRA_KEY_LABEL) ?: ""
        val wasWrong = intent.getBooleanExtra(EXTRA_WRONG, false)

        if (keyId == 0L) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            PGPonyTheme {
                var passphrase by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { cancel() },
                    title = { Text(stringResource(R.string.provider_passphrase_title)) },
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.provider_passphrase_body_format, keyLabel
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (wasWrong) {
                                Text(
                                    stringResource(R.string.provider_passphrase_wrong),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password
                                ),
                                label = {
                                    Text(stringResource(R.string.provider_passphrase_hint))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = passphrase.isNotEmpty(),
                            onClick = {
                                ProviderPassphraseCache.put(keyId, passphrase)
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        ) { Text(stringResource(R.string.provider_passphrase_unlock)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { cancel() }) {
                            Text(stringResource(R.string.common_button_cancel))
                        }
                    }
                )
            }
        }
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

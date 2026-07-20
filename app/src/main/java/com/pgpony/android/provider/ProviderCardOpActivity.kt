// ProviderCardOpActivity.kt
// PGPony Android — 4.0.0 Succession Phase P2c (provider card flow)
//
// The NFC interaction surface for provider operations on card-backed
// keys: "hold your security key" + PIN, then the WHOLE crypto
// operation (sign / sign+encrypt / decrypt) runs during the tap via
// the same card stack the in-app screens use (OpenPgpCardReader in
// reader mode; the operation executes on the NFC binder thread with
// the card present). The result lands in ProviderCardOpStore; the
// client app retries its identical call and the service serves it.
//
// Launched only via the service's PendingIntent (NOT exported). PIN
// behavior mirrors the in-app card screens: pre-filled from
// CardPinCache when a cached PIN is held, remembered after a
// successful op when the cache is enabled, cleared on WrongPin.

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pgpony.android.PGPonyApp
import com.pgpony.android.PGPonyTheme
import com.pgpony.android.R
import com.pgpony.android.crypto.PGPCryptoService
import com.pgpony.android.crypto.card.CardDecryptService
import com.pgpony.android.crypto.card.CardPinCache
import com.pgpony.android.crypto.card.CardSigningService
import com.pgpony.android.crypto.card.OpenPgpCardException
import com.pgpony.android.crypto.card.OpenPgpCardSession
import com.pgpony.android.nfc.OpenPgpCardReader
import kotlinx.coroutines.runBlocking
import org.openintents.openpgp.util.OpenPgpApi

class ProviderCardOpActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OP_KEY = "com.pgpony.android.provider.CARD_OP_KEY"
    }

    private var reader: OpenPgpCardReader? = null
    private var opKey: String = ""

    // Mirrors of Compose state readable from the NFC binder thread.
    @Volatile private var currentPin: String = ""

    // UI state (Compose observes these).
    private val statusText = mutableStateOf<String?>(null)
    private val errorText = mutableStateOf<String?>(null)
    private val working = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        opKey = intent.getStringExtra(EXTRA_OP_KEY) ?: ""
        val op = ProviderCardOpStore.getPending(opKey)
        if (op == null) {
            // Expired or already handled — the client will simply get a
            // fresh interaction on its next call.
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val entity = runBlocking {
            (application as PGPonyApp).keyRepository.getByFingerprint(op.cardEntityFingerprint)
        }
        if (entity == null) {
            ProviderCardOpStore.abandon(opKey)
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val cardLabel = entity.cardManufacturer
            ?: getString(R.string.provider_cardop_generic_card)

        val cachedPin = CardPinCache.retrieve() ?: ""
        currentPin = cachedPin

        setContent {
            PGPonyTheme {
                val pinState = androidx.compose.runtime.remember {
                    mutableStateOf(cachedPin)
                }
                val status by statusText
                val error by errorText
                val busy by working

                AlertDialog(
                    onDismissRequest = { cancel() },
                    title = {
                        Text(stringResource(R.string.provider_cardop_title, cardLabel))
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    when (op.action) {
                                        OpenPgpApi.ACTION_DECRYPT_VERIFY,
                                        OpenPgpApi.ACTION_DECRYPT_METADATA ->
                                            R.string.provider_cardop_body_decrypt
                                        else -> R.string.provider_cardop_body_sign
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = pinState.value,
                                onValueChange = {
                                    pinState.value = it
                                    currentPin = it
                                    errorText.value = null
                                },
                                enabled = !busy,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                ),
                                label = { Text(stringResource(R.string.provider_cardop_pin_hint)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                            status?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                            error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(enabled = !busy, onClick = { cancel() }) {
                            Text(stringResource(R.string.common_button_cancel))
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (opKey.isEmpty()) return
        val r = OpenPgpCardReader(this)
        reader = r
        val started = r.startOperation(
            operation = { session -> performOperation(session) },
            onResult = { result -> onCardResult(result) }
        )
        if (!started) {
            errorText.value = getString(R.string.provider_cardop_nfc_unavailable)
        } else {
            statusText.value = getString(R.string.provider_cardop_waiting)
        }
    }

    override fun onStop() {
        reader?.stop()
        reader = null
        super.onStop()
    }

    /**
     * Runs on the NFC binder thread with the card present. P2c Fix1:
     * mirrors the in-app card flows exactly — SELECT the OpenPGP applet
     * first (a fresh session is unselected; skipping this made the card
     * answer 0x6A80), read the Application Related Data to learn the
     * tapped card's slot fingerprints, resolve the ring via the
     * tolerant offline-primary loader, and guard against the WRONG
     * card being tapped before any PIN is spent.
     */
    private fun performOperation(session: OpenPgpCardSession): ProviderCardOpStore.CompletedOp {
        // P2c Fix3: an OutOfMemoryError (or any Throwable) from a huge
        // attachment must surface as a card error, not escape the
        // reader's Exception-only catch and leave the dialog stuck on
        // "Waiting for the card…".
        return try {
            performOperationInner(session)
        } catch (e: OpenPgpCardException) {
            throw e
        } catch (t: Throwable) {
            throw OpenPgpCardException.Communication(
                t.message ?: getString(R.string.provider_cardop_generic_error),
                t as? Exception
            )
        }
    }

    private fun performOperationInner(session: OpenPgpCardSession): ProviderCardOpStore.CompletedOp {
        working.value = true
        val op = ProviderCardOpStore.getPending(opKey)
            ?: throw OpenPgpCardException.Malformed("Operation expired — try again from your mail app")
        val app = application as PGPonyApp
        val repo = app.keyRepository
        val pinBytes = currentPin.toByteArray(Charsets.UTF_8)
        if (pinBytes.isEmpty()) {
            throw OpenPgpCardException.Malformed(getString(R.string.provider_cardop_pin_needed))
        }

        // P2c Fix1 — the missing SELECT, same as Screens.kt's card ops.
        session.select()
        val ard = session.getApplicationRelatedData()

        fun ringForSlot(slotFingerprint: String?): org.bouncycastle.openpgp.PGPPublicKeyRing {
            val fp = slotFingerprint
                ?: throw OpenPgpCardException.Malformed(
                    getString(R.string.provider_cardop_no_slot_key)
                )
            // 3.1.0 Phase 7 Fix1 loader: slot fps are SUBKEYS on
            // offline-primary layouts.
            val ring = repo.loadPublicKeyRingByCardFingerprint(fp)
                ?: throw OpenPgpCardException.Malformed(
                    getString(R.string.provider_cardop_pair_first)
                )
            // Wrong-card guard: the tapped card's ring must be the key
            // the mail app selected — checked BEFORE any PIN is spent.
            val ringFp = org.bouncycastle.util.encoders.Hex
                .toHexString(ring.publicKey.fingerprint)
            if (!ringFp.equals(op.cardEntityFingerprint, ignoreCase = true)) {
                throw OpenPgpCardException.Malformed(
                    getString(R.string.provider_cardop_wrong_card)
                )
            }
            return ring
        }

        return when (op.action) {
            OpenPgpApi.ACTION_SIGN_AND_ENCRYPT -> {
                val pubRing = ringForSlot(ard.sigFingerprint)
                val recipients = op.recipientFingerprints.mapNotNull { repo.loadPublicKeyRing(it) }
                // P2c Fix3: stream through a temp file with compression
                // OFF. Streaming avoids buffering a 45 MB attachment's
                // ciphertext in memory; skipping ZLIB keeps the card
                // connected only for the brief AES pass (compressing an
                // incompressible video kept the tag alive long enough to
                // go "out of date").
                val outFile = java.io.File.createTempFile("pgpony_cardenc_", ".tmp", cacheDir)
                java.io.ByteArrayInputStream(op.input).use { ins ->
                    java.io.FileOutputStream(outFile).use { outs ->
                        PGPCryptoService.shared.encryptStream(
                            input = ins,
                            output = outs,
                            recipientPublicKeys = recipients,
                            filename = op.filename,
                            armor = op.armor,
                            enableCompression = op.enableCompression,
                            cardSession = session,
                            cardPin = pinBytes,
                            // The [S]-slot key — a SUBKEY on offline-primary
                            // layouts (3.1.0 Phase 7 A3).
                            cardSigningPublicKey =
                                CardSigningService.shared.signingPublicKey(pubRing, ard.sigFingerprint)
                        )
                    }
                }
                ProviderCardOpStore.CompletedOp.StreamFile(outFile)
            }

            OpenPgpApi.ACTION_CLEARTEXT_SIGN, OpenPgpApi.ACTION_SIGN -> {
                val pubRing = ringForSlot(ard.sigFingerprint)
                val signed = CardSigningService.shared.signClear(
                    session,
                    CardSigningService.shared.signingPublicKey(pubRing, ard.sigFingerprint),
                    pinBytes,
                    op.input.toString(Charsets.UTF_8)
                )
                ProviderCardOpStore.CompletedOp.Stream(signed.toByteArray(Charsets.UTF_8))
            }

            OpenPgpApi.ACTION_DETACHED_SIGN -> {
                val pubRing = ringForSlot(ard.sigFingerprint)
                val signature = CardSigningService.shared.signDetached(
                    session,
                    CardSigningService.shared.signingPublicKey(pubRing, ard.sigFingerprint),
                    pinBytes,
                    op.input,
                    armor = op.armor
                )
                ProviderCardOpStore.CompletedOp.Detached(signature, "pgp-sha256")
            }

            OpenPgpApi.ACTION_DECRYPT_VERIFY, OpenPgpApi.ACTION_DECRYPT_METADATA -> {
                val pubRing = ringForSlot(ard.decFingerprint)
                val verificationKeys = runBlocking {
                    repo.getAllKeys().mapNotNull { repo.loadPublicKeyRing(it.fingerprint) }
                }
                val result = CardDecryptService.shared.decryptBytes(
                    session = session,
                    pubRing = pubRing,
                    pin = pinBytes,
                    encrypted = op.input,
                    verificationKeys = verificationKeys
                )
                ProviderCardOpStore.CompletedOp.Decrypted(
                    data = result.data,
                    filename = result.filename,
                    hadSignature = result.hadSignature,
                    signerKnown = result.signerKnown,
                    signatureVerified = result.signatureVerified,
                    signerKeyIdRaw = result.signerKeyID?.let {
                        runCatching { java.lang.Long.parseUnsignedLong(it, 16) }.getOrNull()
                    }
                )
            }

            else -> throw OpenPgpCardException.Malformed("Unsupported card operation: ${op.action}")
        }
    }

    /** Back on the main thread with the operation's outcome. */
    private fun onCardResult(result: Result<ProviderCardOpStore.CompletedOp>) {
        working.value = false
        result.fold(
            onSuccess = { completedOp ->
                if (CardPinCache.isEnabled() && currentPin.isNotEmpty()) {
                    CardPinCache.remember(currentPin)
                }
                ProviderCardOpStore.complete(opKey, completedOp)
                setResult(Activity.RESULT_OK)
                finish()
            },
            onFailure = { e ->
                when (e) {
                    is OpenPgpCardException.WrongPin -> {
                        CardPinCache.clear()
                        errorText.value = getString(
                            R.string.provider_cardop_wrong_pin, e.triesRemaining
                        )
                    }
                    is OpenPgpCardException.TagLost ->
                        errorText.value = getString(R.string.provider_cardop_tag_lost)
                    else ->
                        errorText.value = e.message
                            ?: getString(R.string.provider_cardop_generic_error)
                }
                statusText.value = getString(R.string.provider_cardop_waiting)
                // Reader mode stays engaged; arm for the next tap.
                reader?.rearm()
            }
        )
    }

    private fun cancel() {
        ProviderCardOpStore.abandon(opKey)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

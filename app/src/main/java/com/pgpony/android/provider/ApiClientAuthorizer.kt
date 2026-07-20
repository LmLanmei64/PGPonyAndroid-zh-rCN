// ApiClientAuthorizer.kt
// PGPony Android — 4.0.0 Succession Phase 1 (OpenPGP API provider)
//
// Decides whether a bound OpenPGP API client may talk to the provider
// service. The decision inputs are deliberately narrow so the class is
// unit-testable without Android: a DAO (allow-list rows) and a
// signature provider function (package name → lowercase hex SHA-256 of
// the signing cert, or null if unresolvable). The Android-specific
// signature lookup lives in the companion and is injected by the
// service; tests inject a fake.
//
// Decision table (no default-allow, ever):
//   • no row for the package            → UNKNOWN   (consent flow)
//   • row exists, signature matches     → AUTHORIZED
//   • row exists, signature differs     → SIGNATURE_MISMATCH (hard error;
//     the user must revoke the stale row in Settings → Connected apps
//     and re-consent — deliberate friction, this is the impostor case)
//   • signature unresolvable            → UNRESOLVABLE (hard error)

package com.pgpony.android.provider

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import com.pgpony.android.data.ApiClientDao
import com.pgpony.android.data.ApiClientEntity
import java.security.MessageDigest

class ApiClientAuthorizer(
    private val dao: ApiClientDao,
    private val signatureSha256Of: (packageName: String) -> String?
) {

    enum class Decision {
        AUTHORIZED,
        UNKNOWN,
        SIGNATURE_MISMATCH,
        UNRESOLVABLE
    }

    suspend fun authorize(packageName: String): Decision {
        val currentSig = signatureSha256Of(packageName)
            ?: return Decision.UNRESOLVABLE
        val row = dao.getByPackage(packageName)
            ?: return Decision.UNKNOWN
        return if (row.signatureSha256.equals(currentSig, ignoreCase = true)) {
            Decision.AUTHORIZED
        } else {
            Decision.SIGNATURE_MISMATCH
        }
    }

    /**
     * Record user consent for this package, pinning its CURRENT signing
     * certificate. Returns false when the signature can't be resolved
     * (app uninstalled between consent tap and grant — don't store an
     * unpinned row).
     */
    suspend fun grant(packageName: String, grantedAt: Long = System.currentTimeMillis()): Boolean {
        val sig = signatureSha256Of(packageName) ?: return false
        dao.insert(ApiClientEntity(packageName, sig.lowercase(), grantedAt))
        return true
    }

    suspend fun revoke(packageName: String) {
        dao.deleteByPackage(packageName)
    }

    companion object {
        /**
         * Android-side signature lookup: lowercase hex SHA-256 of the
         * package's first signing certificate. API 28+ uses the modern
         * signing-info API; 26–27 falls back to the deprecated
         * GET_SIGNATURES (safe here — we only READ the cert to hash it,
         * and the known fake-ID vulnerabilities concern chain VALIDATION,
         * which we don't do).
         *
         * Rotated keys: we hash the CURRENT cert (last in the rotation
         * history). A client that rotates its signing key will show as
         * SIGNATURE_MISMATCH and needs one re-consent — same behavior
         * OpenKeychain exhibits, and the safe default.
         */
        @SuppressLint("PackageManagerGetSignatures")
        @Suppress("DEPRECATION")
        fun platformSignatureLookup(pm: PackageManager): (String) -> String? = lookup@{ packageName ->
            try {
                val certBytes: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = pm.getPackageInfo(
                        packageName, PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    val signingInfo = info.signingInfo ?: return@lookup null
                    val signers = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    // signingCertificateHistory is ordered oldest → newest;
                    // pin the newest (current) certificate.
                    signers?.lastOrNull()?.toByteArray() ?: return@lookup null
                } else {
                    val info = pm.getPackageInfo(
                        packageName, PackageManager.GET_SIGNATURES
                    )
                    info.signatures?.firstOrNull()?.toByteArray() ?: return@lookup null
                }
                MessageDigest.getInstance("SHA-256")
                    .digest(certBytes)
                    .joinToString("") { "%02x".format(it) }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

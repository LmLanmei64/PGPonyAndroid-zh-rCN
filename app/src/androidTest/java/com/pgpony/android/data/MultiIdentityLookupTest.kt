// MultiIdentityLookupTest.kt
// PGPony Android — 4.2.1 (#27, bluemle)
//
// Proves the fix for #27: a key carrying a SECONDARY identity (added via
// the 4.2.0 multiple-identities feature) must be resolvable by that
// secondary address. The indexed userEmail column holds only the primary
// address, so getByEmail cannot find it and getByAnyUserEmail must.
//
// This is the repository-level heart of the bug the provider surfaced:
// a mail client resolves a recipient address through the provider, which
// resolves through these methods. The provider fix is just routing the
// four ACTION sites to getByAnyUserEmail; the behavior under test is here.
//
// SCREEN MUST BE ON AND UNLOCKED is not relevant (no Compose), but this is
// an instrumented test because getByAnyUserEmail parses stored key blobs
// through the real crypto + storage layers.

package com.pgpony.android.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pgpony.android.PGPonyApp
import com.pgpony.android.crypto.KeyAlgorithm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiIdentityLookupTest {

    private val app: PGPonyApp = ApplicationProvider.getApplicationContext()

    private companion object {
        const val PRIMARY = "primary-2701@pgpony.invalid"
        const val SECONDARY = "secondary-2701@pgpony.invalid"
    }

    @Test
    fun secondaryIdentity_resolvesViaAnyUserEmail_notViaPrimaryOnly() = runBlocking {
        val repo = app.keyRepository

        // Fresh key with PRIMARY as its only identity.
        val entity = repo.generateKey(
            name = "Multi Identity 2701",
            email = PRIMARY,
            algorithm = KeyAlgorithm.ED25519_CV25519,
            passphrase = null
        )
        try {
            // Add SECONDARY as a non-primary identity (the 4.2.0 #29 path).
            repo.addUserId(entity.fingerprint, "Alt <$SECONDARY>", makePrimary = false, passphrase = null)

            // The bug: the primary-only indexed lookup does NOT find the
            // secondary address.
            assertTrue(
                "getByEmail should miss a secondary identity (documents the bug)",
                repo.getByEmail(SECONDARY).none { it.fingerprint == entity.fingerprint }
            )

            // The fix: the union lookup DOES find it, by fingerprint.
            val hit = repo.getByAnyUserEmail(SECONDARY)
            assertTrue(
                "getByAnyUserEmail must resolve the secondary identity",
                hit.any { it.fingerprint == entity.fingerprint }
            )

            // The primary address still resolves through both, and case
            // does not matter for the union lookup.
            assertTrue(repo.getByAnyUserEmail(PRIMARY).any { it.fingerprint == entity.fingerprint })
            assertTrue(repo.getByAnyUserEmail(SECONDARY.uppercase()).any { it.fingerprint == entity.fingerprint })

            // A truly absent address resolves to nothing.
            assertEquals(0, repo.getByAnyUserEmail("nobody-2701@pgpony.invalid").size)
        } finally {
            repo.deleteByFingerprint(entity.fingerprint)
        }
    }
}

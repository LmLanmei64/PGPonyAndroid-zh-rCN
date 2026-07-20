// ApiClientAuthorizerTest.kt
// PGPony Android — 4.0.0 Succession Phase 1
//
// Contract tests for the provider's authorization decision table.
// Pure JVM: the DAO is an in-memory fake and the signature lookup is a
// mutable map, so every branch of the decision table runs without a
// device. The one Android-specific piece (platformSignatureLookup) is
// exercised by the instrumented handshake test instead.

package com.pgpony.android.provider

import com.pgpony.android.data.ApiClientDao
import com.pgpony.android.data.ApiClientEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeApiClientDao : ApiClientDao {
    val rows = LinkedHashMap<String, ApiClientEntity>()

    override suspend fun getAll(): List<ApiClientEntity> =
        rows.values.sortedByDescending { it.grantedAt }

    override suspend fun getByPackage(packageName: String): ApiClientEntity? =
        rows[packageName]

    override suspend fun insert(client: ApiClientEntity) {
        rows[client.packageName] = client
    }

    override suspend fun deleteByPackage(packageName: String) {
        rows.remove(packageName)
    }

    override suspend fun count(): Int = rows.size
}

class ApiClientAuthorizerTest {

    private val dao = FakeApiClientDao()
    private val signatures = mutableMapOf<String, String?>()
    private val authorizer = ApiClientAuthorizer(dao) { pkg -> signatures[pkg] }

    private val tbird = "net.thunderbird.android"
    private val sigA = "aa".repeat(32)
    private val sigB = "bb".repeat(32)

    @Test
    fun unknownPackage_isUnknown_notAuthorized() = runTest {
        signatures[tbird] = sigA
        assertEquals(ApiClientAuthorizer.Decision.UNKNOWN, authorizer.authorize(tbird))
    }

    @Test
    fun grantThenAuthorize_isAuthorized() = runTest {
        signatures[tbird] = sigA
        assertTrue(authorizer.grant(tbird, grantedAt = 1_000L))
        assertEquals(ApiClientAuthorizer.Decision.AUTHORIZED, authorizer.authorize(tbird))
        assertEquals(sigA, dao.getByPackage(tbird)?.signatureSha256)
        assertEquals(1_000L, dao.getByPackage(tbird)?.grantedAt)
    }

    @Test
    fun signatureCase_isInsensitive() = runTest {
        signatures[tbird] = sigA.uppercase()
        assertTrue(authorizer.grant(tbird))
        // Stored lowercase, current reads uppercase — must still match.
        assertEquals(ApiClientAuthorizer.Decision.AUTHORIZED, authorizer.authorize(tbird))
    }

    @Test
    fun changedSignature_isMismatch_neverAuthorized() = runTest {
        signatures[tbird] = sigA
        assertTrue(authorizer.grant(tbird))
        // The impostor case: same package name, different signing cert.
        signatures[tbird] = sigB
        assertEquals(
            ApiClientAuthorizer.Decision.SIGNATURE_MISMATCH,
            authorizer.authorize(tbird)
        )
    }

    @Test
    fun revoke_returnsToUnknown() = runTest {
        signatures[tbird] = sigA
        assertTrue(authorizer.grant(tbird))
        authorizer.revoke(tbird)
        assertEquals(ApiClientAuthorizer.Decision.UNKNOWN, authorizer.authorize(tbird))
        assertNull(dao.getByPackage(tbird))
    }

    @Test
    fun unresolvableSignature_isHardError_notConsent() = runTest {
        signatures[tbird] = null
        assertEquals(ApiClientAuthorizer.Decision.UNRESOLVABLE, authorizer.authorize(tbird))
    }

    @Test
    fun grantWithUnresolvableSignature_refusesToStoreUnpinnedRow() = runTest {
        signatures[tbird] = null
        assertFalse(authorizer.grant(tbird))
        assertNull(dao.getByPackage(tbird))
    }

    @Test
    fun reGrantAfterSignatureChange_overwritesPin() = runTest {
        signatures[tbird] = sigA
        assertTrue(authorizer.grant(tbird))
        signatures[tbird] = sigB
        // User explicitly re-consents (REPLACE conflict strategy).
        assertTrue(authorizer.grant(tbird))
        assertEquals(ApiClientAuthorizer.Decision.AUTHORIZED, authorizer.authorize(tbird))
        assertEquals(sigB, dao.getByPackage(tbird)?.signatureSha256)
    }
}

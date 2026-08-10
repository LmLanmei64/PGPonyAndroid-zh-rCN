// FontScaleReachabilityTest.kt
// PGPony Android - 4.2.0 RC3 workstream K (planning 15.2's durable half)
//
// The class-of-bug this guards: a surface with a bottom primary action
// that fits the reference device at fontScale 1.0 but clips the button
// off-screen at a larger font/display scale. Two releases in a row
// shipped one (#23 GenerateKeySheet in 4.1.0, then the onboarding
// surface suspected for the rc2 report), each caught by a user instead
// of a test. This is the test.
//
// Mechanics: each surface renders under a Density override - fontScale
// 1.3 AND density x1.35, the latter simulating the "largest display
// size" setting (higher density = fewer dp of viewport for the same
// pixels). The assertion is a best-effort performScrollTo() (surfaces
// that pin the action without scroll BY DESIGN have no scrollable
// ancestor, which is fine as long as the action is visible) followed by
// a hard assertIsDisplayed(). A surface that lost its verticalScroll
// AND clips the button at this scale fails the visibility check.
//
// RUN REQUIREMENT: the device screen must be ON and UNLOCKED. With the
// screen off, activities launch but never resume or lay out, so every
// compose test here fails with "no compose hierarchies found" - a
// symptom that looks like broken test infrastructure and cost a full
// debugging round to identify. (Developer options -> Stay awake helps.)
//
// The host window always renders an anchor node alongside any
// sheet-hosted surface: the ModalBottomSheet content lives in its own
// window, and a main window with no compose nodes at all makes the
// semantics lookup abort with "no compose hierarchies found" before it
// ever reaches the sheet's window.

package com.pgpony.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pgpony.android.PGPonyApp
import com.pgpony.android.R
import com.pgpony.android.ui.keyring.AddUserIdSheet
import com.pgpony.android.ui.keyring.DeleteKeySheet
import com.pgpony.android.ui.keyring.KeyringViewModel
import com.pgpony.android.ui.onboarding.OnboardingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontScaleReachabilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    private val hostAnchor = "fontScaleTestHost"

    /** fontScale 1.3 + largest-display density, per the 15.2 plan. */
    private fun setScaledContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = base.density * 1.35f,
                    fontScale = 1.3f
                )
            ) {
                // Anchor: keeps the main window's compose hierarchy
                // non-empty when [content] is entirely sheet-hosted.
                Text(hostAnchor)
                content()
            }
        }
    }

    private fun assertReachable(buttonText: String) {
        // hasClickAction() disambiguates the ACTION from a title or
        // label that happens to carry the same text (the Add User ID
        // sheet's title and button both read "Add User ID").
        val node = composeRule.onNode(hasText(buttonText) and hasClickAction())
        try {
            node.performScrollTo()
        } catch (e: AssertionError) {
            // No scrollable ancestor - legal for pinned-bottom layouts;
            // the visibility assert below is the real gate.
        } catch (e: IllegalStateException) {
            // Same: scroll is best-effort.
        }
        node.assertIsDisplayed()
    }

    @Test
    fun onboardingScreen_primaryActionReachable() {
        val context = composeRule.activity
        setScaledContent {
            OnboardingScreen(
                prefs = context.getSharedPreferences("fontscale_test_prefs", 0),
                keyringVm = KeyringViewModel(PGPonyApp.instance.keyRepository),
                onComplete = {},
                onImportExisting = {},
                onRestoreBackup = {}
            )
        }
        assertReachable(string(R.string.onboarding_screen_next))
    }

    @Test
    fun deleteKeySheet_primaryActionReachable() {
        setScaledContent {
            DeleteKeySheet(
                keyOwnerLabel = "Font Scale Test",
                shortFingerprint = "ABCD1234EF567890",
                onSaveBackup = {},
                onDelete = {},
                onDismiss = {}
            )
        }
        assertReachable(string(R.string.key_delete_confirm_button))
    }

    @Test
    fun addUserIdSheet_primaryActionReachable() {
        setScaledContent {
            AddUserIdSheet(
                keyOwnerLabel = "Font Scale Test",
                onApply = { _, _, _ -> },
                onDismiss = {}
            )
        }
        assertReachable(string(R.string.key_detail_add_userid_apply))
    }
}

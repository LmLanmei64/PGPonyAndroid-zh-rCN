// Autofill.kt
// PGPony Android — 4.1.0, section 3 (issue #8, NinthRebuild)
//
// Make PGPony's passphrase fields visible to Android's autofill framework,
// so a password manager (KeePassDX, Keepass2Android) can offer a fill the
// way it did for OpenKeychain.
//
// Why anything is needed at all: OpenKeychain's passphrase prompt is a
// classic EditText with inputType=textPassword, which the platform's
// autofill heuristics classify for free. A Compose text field is a single
// drawn surface with no view hierarchy behind it, so the framework sees
// nothing to classify unless the composition explicitly publishes an
// autofill node. It does not; hence the reporter falling back to
// Magikeyboard to get his passphrase in.
//
// Why the older API: this module builds against compose-bom 2024.12.01
// (Compose UI 1.7.6). The declarative `ContentType.Password` semantics
// arrived in 1.8, so the node-based API below is what is actually
// available here. It is @ExperimentalComposeUiApi and superseded in 1.8 —
// when the BOM moves, this file becomes a one-line semantics modifier and
// every call site keeps its current shape. That is deliberate: the call
// sites depend on `Modifier.autofillPassword { … }`, not on how it works.
//
// Scope note (4.1.0 plan §3.5): this is for PASSPHRASES that unlock a key.
// Smartcard PINs are deliberately not wired up — putting a card PIN in a
// password manager is a reasonable thing to want and a separate security
// conversation, and it should not arrive silently as a side effect of this.

package com.pgpony.android.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Publish this field to the autofill framework as a password, and route a
 * chosen credential back through [onFill].
 *
 * [onFill] should do exactly what the field's own `onValueChange` does — the
 * fill is a value change that happens to come from outside the keyboard.
 *
 * Usage:
 * ```
 * OutlinedTextField(
 *     value = state.passphrase,
 *     onValueChange = { viewModel.updatePassphrase(it) },
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .autofillPassword { viewModel.updatePassphrase(it) },
 * )
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.autofillPassword(onFill: (String) -> Unit): Modifier =
    autofillFor(listOf(AutofillType.Password), onFill)

/**
 * General form of [autofillPassword]. [autofillTypes] is the ordered list of
 * hints the framework matches against; the first one a manager understands
 * wins.
 *
 * The node has to be registered with the composition-local autofill tree AND
 * given a bounding box, because the framework addresses it as a virtual view
 * inside the Compose surface — without a box there is nothing on screen for
 * the fill UI to anchor to. Requesting on focus (and cancelling on blur) is
 * what makes the suggestion appear when the user taps the field, rather than
 * only on an explicit "Autofill" long-press.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.autofillFor(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit
): Modifier {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    // The node outlives individual recompositions, but the callback it holds
    // must not: capturing the first onFill would keep filling into a stale
    // ViewModel reference after a configuration change.
    val currentOnFill by rememberUpdatedState(onFill)
    val node = remember {
        AutofillNode(
            autofillTypes = autofillTypes,
            onFill = { value -> currentOnFill(value) }
        )
    }

    DisposableEffect(node) {
        autofillTree += node
        onDispose { autofillTree.children.remove(node.id) }
    }

    return this
        .onGloballyPositioned { coordinates ->
            node.boundingBox = coordinates.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            val service = autofill ?: return@onFocusChanged
            // The Autofill interface names these per-node, and cancel takes
            // the node too — there is no ambient "cancel whatever is running".
            if (focusState.isFocused) {
                service.requestAutofillForNode(node)
            } else {
                service.cancelAutofillForNode(node)
            }
        }
}

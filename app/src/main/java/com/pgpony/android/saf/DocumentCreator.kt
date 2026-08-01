// DocumentCreator.kt
// PGPony Android — 4.1.0 Phase 7b (issue #13)
//
// The system file creator (ACTION_CREATE_DOCUMENT), owned by no activity
// in particular.
//
// It used to live on MainActivity as startDocumentCreator + a pending
// callback + a branch of onActivityResult. Six result screens reached it by
// walking the ContextWrapper chain up to MainActivity BY NAME, each with its
// own private copy of the walk (findResultMainActivity,
// findFileResultMainActivity, findBundleResultMainActivity, and so on).
//
// Inside ShareTargetActivity every one of those walks returns null, because
// the share target is a different activity. That is issue #13: share a file
// in, encrypt it, and there is nothing but Share and Done — no Save, because
// the capability was attached to a class rather than to a capability.
//
// So: an interface for what a screen actually needs, one walk instead of
// six-plus-one, and the launcher plumbing in a small object either activity
// can own. MainActivity's public method keeps its exact signature, so its
// existing call sites did not change at all.

package com.pgpony.android.saf

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.app.ActivityCompat

/**
 * An activity that can ask the system where to write a file.
 *
 * Screens depend on this rather than on a concrete activity, which is what
 * lets the same result screen run inside the main app and inside the share
 * target.
 */
interface DocumentCreatorHost {
    /**
     * Open the system file creator and call back with the destination the
     * user chose, or null if they cancelled or no picker exists.
     *
     * The caller writes to the URI itself, via
     * contentResolver.openOutputStream(uri).
     */
    fun startDocumentCreator(
        mimeType: String,
        suggestedName: String,
        callback: (Uri?) -> Unit,
    )
}

/**
 * Walk the ContextWrapper chain to whichever activity is hosting this
 * composition, if it can create documents.
 *
 * Compose hands screens a wrapped Context, so the cast has to unwrap first —
 * the same walk the result screens each carried privately, written once and
 * asking about a capability instead of about MainActivity.
 */
fun Context.findDocumentCreatorHost(): DocumentCreatorHost? = when (this) {
    is DocumentCreatorHost -> this
    is ContextWrapper -> baseContext.findDocumentCreatorHost()
    else -> null
}

/**
 * The ACTION_CREATE_DOCUMENT launcher, holding the one piece of state this
 * needs: the callback waiting on a result.
 *
 * startActivityForResult rather than the ActivityResult APIs because that is
 * what the sibling pickers on MainActivity already use, and mixing the two
 * registration models in one activity is how you end up with a launcher
 * registered after onCreate and a crash on the first save.
 *
 * @param requestCode must be in [1, 65535] — FragmentActivity masks against
 *   0xFFFF0000 and rejects anything larger.
 * @param onBusy called with true while the picker is in front and false once
 *   it is gone. MainActivity uses it to hold off the biometric auto-lock; the
 *   share target has no lock gate and leaves it at the default.
 */
class SafDocumentCreator(
    private val requestCode: Int,
    private val onBusy: (Boolean) -> Unit = {},
) {
    private var pending: ((Uri?) -> Unit)? = null

    fun launch(
        activity: Activity,
        mimeType: String,
        suggestedName: String,
        callback: (Uri?) -> Unit,
    ) {
        pending = callback
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        try {
            onBusy(true)
            ActivityCompat.startActivityForResult(activity, intent, requestCode, null)
        } catch (e: ActivityNotFoundException) {
            // A device with no documents provider at all. Rare, but the
            // caller must hear something back or its UI waits forever.
            onBusy(false)
            pending = null
            callback(null)
        }
    }

    /**
     * Feed an activity result in. Returns true when this creator owned the
     * request code and consumed it, so a host with several pickers can chain
     * them without a when-block.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != this.requestCode) return false
        onBusy(false)
        val cb = pending
        pending = null
        cb?.invoke(if (resultCode == Activity.RESULT_OK) data?.data else null)
        return true
    }
}

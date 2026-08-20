// TrustMark.kt
// PGPony Android — 4.3.0 RC4 #24 (AraafRoyall: trust icon swap)
//
// Single source of truth for the trust ladder. Both KeyCard/KeyDetail and
// the Contacts badge render through TrustMark so the two surfaces can never
// drift again (they had disagreed: Star vs WorkspacePremium for Ultimate,
// and different amber/grey hexes). Shape AND color climb with rank:
//
//   UNKNOWN    shield + "?"   grey    (GppMaybe)
//   UNVERIFIED shield + "!"   amber   (GppBad)
//   VERIFIED   shield + check green   (GppGood)
//   ULTIMATE   star on shield purple  (composite: white Star over a filled
//                                      Shield — one rung above the trio)

package com.pgpony.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pgpony.android.data.TrustLevel

/** The trust palette, defined once. Semantic ladder grey → amber → green,
 *  then the app accent purple for the top rung. */
object TrustColors {
    val Unknown = Color(0xFF9CA3AF)    // grey-400
    val Unverified = Color(0xFFF59E0B) // amber-500
    val Verified = Color(0xFF22C55E)   // green-500
    val Ultimate = Color(0xFF8B5CF6)   // violet-500, the primary-UID accent
}

fun trustColor(trust: TrustLevel): Color = when (trust) {
    TrustLevel.UNKNOWN -> TrustColors.Unknown
    TrustLevel.UNVERIFIED -> TrustColors.Unverified
    TrustLevel.VERIFIED -> TrustColors.Verified
    TrustLevel.ULTIMATE -> TrustColors.Ultimate
}

/**
 * Render the trust mark for [trust] at [size]. The bottom three levels are
 * single shield glyphs; Ultimate is a white star punched through a filled
 * purple shield, nudged up a hair for the shield's optical center.
 */
@Composable
fun TrustMark(
    trust: TrustLevel,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = trust.localizedName()
) {
    val tint = trustColor(trust)
    val shield: ImageVector? = when (trust) {
        TrustLevel.UNKNOWN -> Icons.Filled.GppMaybe
        TrustLevel.UNVERIFIED -> Icons.Filled.GppBad
        TrustLevel.VERIFIED -> Icons.Filled.GppGood
        TrustLevel.ULTIMATE -> null
    }
    if (shield != null) {
        Icon(
            imageVector = shield,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size)
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size)
            )
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(size * 0.46f)
                    .offset(y = -(size * 0.045f))
            )
        }
    }
}

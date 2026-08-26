package dev.jay.betterconnect.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Amber on charcoal, borrowed from the negative-LCD instrument cluster this app talks to.
 * Used as the fallback when dynamic colour is unavailable or switched off.
 */
internal object Palette {
    val Amber10 = Color(0xFF2A1C00)
    val Amber20 = Color(0xFF463100)
    val Amber30 = Color(0xFF644700)
    val Amber40 = Color(0xFF855F00)
    val Amber80 = Color(0xFFFFB951)
    val Amber90 = Color(0xFFFFDDB3)

    val Teal40 = Color(0xFF00696E)
    val Teal80 = Color(0xFF4CD9E0)
    val Teal90 = Color(0xFFB0ECF0)

    val Neutral10 = Color(0xFF1B1B1F)
    val Neutral20 = Color(0xFF303034)
    val Neutral90 = Color(0xFFE4E2E6)
    val Neutral95 = Color(0xFFF3F0F4)
    val Neutral99 = Color(0xFFFDFBFF)

    val Red40 = Color(0xFFBA1A1A)
    val Red80 = Color(0xFFFFB4AB)
    val Red90 = Color(0xFFFFDAD6)

    val Green40 = Color(0xFF2E6B37)
    val Green80 = Color(0xFF9BD49F)
}

/** Semantic colours for link state and write outcomes, outside the Material roles. */
object StatusColors {
    val Ok = Color(0xFF3FA35A)
    val Warn = Color(0xFFE0A030)
    val Error = Color(0xFFD3453C)
    val Idle = Color(0xFF8A8A8E)

    /** The negative LCD: light glyphs on a near-black panel. */
    val ClusterPanel = Color(0xFF14161A)
    val ClusterPanelEdge = Color(0xFF2B2F36)
    val ClusterInk = Color(0xFFE8EDF2)
    val ClusterInkDim = Color(0xFF6E7681)
}

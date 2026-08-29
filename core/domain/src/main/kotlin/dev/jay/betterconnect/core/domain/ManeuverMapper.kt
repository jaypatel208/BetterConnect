package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.Symbol

/**
 * `symbol == null` means "no confident mapping - hold whatever icon is already showing."
 * Never [Symbol.ROTARY] as a fallback (`B` draws a rotary arc on this cluster, so it would
 * tell the rider they are entering a roundabout that does not exist) and never a hardware-
 * inert code. See `MANEUVERS.md` §6 and `IMPLEMENTATION.md` §4.
 */
data class MappedManeuver(val symbol: Symbol?, val caption: String)

/**
 * Routes API `Maneuver` → cluster [Symbol] + caption.
 *
 * **This is a new table, not `MANEUVERS.md` §4** - that table is the *Navigation SDK*
 * `Maneuver` vocabulary (`TURN_KEEP_LEFT`, `ON_RAMP_SLIGHT_LEFT`, …), a different enum from
 * the one Routes API actually returns (`docs/DEVELOPMENT-NOTES.md`, tracker note on this
 * plan). The 21 values below are Routes API's own `Maneuver` enum, verified against Google's
 * current API reference (2026-08-29) rather than assumed from the Navigation SDK table.
 *
 * Hardware constraints applied on top of intent (`MANEUVERS.md` §6):
 * - Never emit `M S T U W Y N` - all inert, and the cluster **holds the previous icon**, so
 *   an inert code shows a stale instruction that looks current (trap B1).
 * - Roundabouts have no reliable icon here (`N`/`U` inert, `B` unconfirmed and risks being
 *   read as "wrong way"/rotary) - fall back to the turn direction the roundabout exits toward.
 * - Ramps use `K`/`L`, left unused by the vendor. Ferries map to `G` (no ferry icon exists).
 * - `C`/`D` are keep/fork and `Z`/`X` are slight - inverted vs the vendor's `PrimaryTurns`
 *   labels; trust the hardware observation over the label.
 * - U-turn handedness (`O` vs `P`) is unconfirmed on hardware (tracker D5); this mapping's
 *   choice is a best guess, flagged here so it is the first thing revisited once resolved.
 */
object ManeuverMapper {

    private val captions: Map<String, Pair<Symbol?, String>> = mapOf(
        "MANEUVER_UNSPECIFIED" to (null to ""),
        "TURN_SLIGHT_LEFT" to (Symbol.SLIGHT_LEFT to "SLIGHT LEFT"),
        "TURN_SHARP_LEFT" to (Symbol.SHARP_LEFT to "SHARP LEFT"),
        // D5: handedness unconfirmed. U_TURN ('P') chosen for LEFT, pending hardware test.
        "UTURN_LEFT" to (Symbol.U_TURN to "U TURN"),
        "TURN_LEFT" to (Symbol.LEFT to "TURN LEFT"),
        "TURN_SLIGHT_RIGHT" to (Symbol.SLIGHT_RIGHT to "SLIGHT RIGHT"),
        "TURN_SHARP_RIGHT" to (Symbol.SHARP_RIGHT to "SHARP RIGHT"),
        "UTURN_RIGHT" to (Symbol.U_TURN_RIGHT_HAND to "U TURN"),
        "TURN_RIGHT" to (Symbol.RIGHT to "TURN RIGHT"),
        "STRAIGHT" to (Symbol.STRAIGHT to "CONTINUE"),
        "RAMP_LEFT" to (Symbol.RAMP_LEFT to "EXIT LEFT"),
        "RAMP_RIGHT" to (Symbol.RAMP_RIGHT to "EXIT RIGHT"),
        "MERGE" to (Symbol.MERGE to "MERGE"),
        "FORK_LEFT" to (Symbol.KEEP_LEFT_FORK to "KEEP LEFT"),
        "FORK_RIGHT" to (Symbol.KEEP_RIGHT_FORK to "KEEP RIGHT"),
        // No ferry icon on this cluster - fall back to straight rather than an inert code.
        "FERRY" to (Symbol.STRAIGHT to "FERRY"),
        "FERRY_TRAIN" to (Symbol.STRAIGHT to "FERRY"),
        // Both roundabout codes (N/U) are inert - fall back to the exit turn direction.
        "ROUNDABOUT_LEFT" to (Symbol.LEFT to "ROUNDABOUT"),
        "ROUNDABOUT_RIGHT" to (Symbol.RIGHT to "ROUNDABOUT"),
        "DEPART" to (Symbol.STRAIGHT to "CONTINUE"),
        "NAME_CHANGE" to (Symbol.STRAIGHT to "CONTINUE"),
    )

    /** Codes that must never be emitted on this cluster - see trap B1. */
    private val inert = setOf('M', 'S', 'T', 'U', 'W', 'Y', 'N')

    fun map(maneuver: String): MappedManeuver {
        val (symbol, caption) = captions[maneuver] ?: (null to "")
        check(symbol == null || symbol.letter !in inert) {
            "ManeuverMapper produced an inert code for $maneuver - fix the table, never the caller"
        }
        return MappedManeuver(symbol, caption)
    }
}

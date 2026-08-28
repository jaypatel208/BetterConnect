package dev.jay.betterconnect.core.model

/**
 * Cluster icon codes for byte 1 of the TBT_INFO frame.
 *
 * The cluster's navigation area is a fixed icon set, so one byte selects one of a
 * closed list. The code is an ASCII letter: uppercase renders steady, lowercase
 * (`+0x20`) renders blinking.
 *
 * Meanings recovered from the official app's `maneuverIDMap` cross-referenced with
 * the sprite filename drawn for the same Mappls maneuver id. See MANEUVERS.md §1.
 */
enum class Symbol(val letter: Char, val label: String) {
    LEFT('I', "Turn left"),
    RIGHT('J', "Turn right"),
    SHARP_LEFT('E', "Sharp left"),
    SHARP_RIGHT('F', "Sharp right"),
    KEEP_LEFT_FORK('Q', "Keep left at fork"),
    KEEP_RIGHT_FORK('R', "Keep right at fork"),
    KEEP_LEFT('C', "Keep left"),
    KEEP_RIGHT('D', "Keep right"),
    SLIGHT_LEFT('Z', "Slight left"),
    SLIGHT_RIGHT('X', "Slight right"),
    STRAIGHT('G', "Straight ahead"),
    U_TURN('P', "U-turn"),
    U_TURN_RIGHT_HAND('O', "U-turn (right hand)"),
    ROUNDABOUT('U', "Roundabout (family U)"),
    ROUNDABOUT_ALT('N', "Roundabout (family N)"),
    ROTARY('B', "Enter the rotary"),
    RAMP_LEFT('K', "Ramp left"),
    RAMP_RIGHT('L', "Ramp right"),
    ARRIVE('G', "Arrive at destination"),
    ;

    val steadyCode: Int get() = letter.code

    /**
     * Blinking is `+0x20` for every entry except arrival, which uses 'H' (0x48)
     * against a steady 'G' (0x47). That exception is in the original encoder.
     */
    val blinkCode: Int get() = if (this == ARRIVE) 'H'.code else letter.code + 0x20

    fun code(blinking: Boolean): Int = if (blinking) blinkCode else steadyCode
}

/**
 * Every code the cluster might be asked to draw, with what we believe it means.
 *
 * Used by the diagnostic sweep: pressing every letter A-Z on a real cluster is the
 * only way to produce a definitive icon table for a specific unit, and resolves the
 * two questions the APK could not answer (N vs U roundabouts, and symbol Y).
 */
object SymbolCatalog {

    /** Documented but unresolved: the sprite for Mappls maneuver 36 was not recoverable. */
    const val UNKNOWN_LABEL: String = "Unknown - sweep to identify"

    private val bySteadyLetter: Map<Char, String> = buildMap {
        // STRAIGHT and ARRIVE share 'G'; only the blink form distinguishes them.
        put('G', "Straight ahead / arrive (steady)")
        put('H', "Arrive (blinking)")
        put('Y', UNKNOWN_LABEL)
        Symbol.entries.forEach { symbol ->
            if (symbol != Symbol.STRAIGHT && symbol != Symbol.ARRIVE) {
                put(symbol.letter, symbol.label)
            }
        }
    }

    fun labelFor(letter: Char): String = bySteadyLetter[letter.uppercaseChar()] ?: UNKNOWN_LABEL

    fun isDocumented(letter: Char): Boolean =
        bySteadyLetter[letter.uppercaseChar()]?.let { it != UNKNOWN_LABEL } == true

    /** The full sweep range for the diagnostic screen. */
    val sweepLetters: List<Char> = ('A'..'Z').toList()
}

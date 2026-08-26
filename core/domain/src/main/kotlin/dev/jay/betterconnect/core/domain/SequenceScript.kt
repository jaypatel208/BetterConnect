package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol

/** One frame in a scripted test, with the reason it is there. */
data class SequenceStep(
    val label: String,
    val nav: NavState,
    /** Shown in the UI while this step is live, so the tester knows what to look for. */
    val note: String? = null,
)

data class SequenceScript(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<SequenceStep>,
    val defaultDwellMs: Long = 2_000L,
)

/**
 * The scripted tests.
 *
 * These exist because bike time is the scarce resource: each script is designed so one
 * run answers a question that cannot be answered from the decompiled app.
 */
object SequenceScripts {

    private fun step(
        label: String,
        symbol: Symbol,
        turnM: Int,
        note: String? = null,
        exit: Int = 0,
        text: String = label.uppercase(),
        totalM: Int = 8_400,
    ) = SequenceStep(
        label = label,
        note = note,
        nav = NavState(
            symbol = symbol,
            distanceToTurnM = turnM,
            distanceLeftM = totalM,
            etaSeconds = 12 * 60,
            text = text,
            roundaboutExit = exit,
        ),
    )

    /**
     * A plausible route, with the two roundabout families placed back to back.
     *
     * The APK contains two symmetrical 7-entry roundabout blocks ('N' and 'U') and gives
     * no way to tell which one a given cluster renders. Showing both in succession answers
     * it by observation in a single run.
     */
    val ROUTE_WALK = SequenceScript(
        id = "route_walk",
        name = "Route walk",
        description = "A realistic route. Also settles N vs U roundabouts by showing both back to back.",
        steps = listOf(
            step("Straight", Symbol.STRAIGHT, 1_200, text = "MG ROAD"),
            step("Left far", Symbol.LEFT, 800, text = "SP RING ROAD"),
            step("Left near", Symbol.LEFT, 300, text = "SP RING ROAD"),
            step("Left blinking", Symbol.LEFT, 80, note = "Under 100 m - the icon should blink", text = "SP RING ROAD"),
            step("Right", Symbol.RIGHT, 600, text = "ASHRAM ROAD"),
            step("Slight right", Symbol.SLIGHT_RIGHT, 400, text = "CG ROAD"),
            step("Roundabout U exit 2", Symbol.ROUNDABOUT, 250, note = "Family U - does this draw a roundabout?", exit = 2, text = "CIRCLE"),
            step("Roundabout N exit 2", Symbol.ROUNDABOUT_ALT, 250, note = "Family N - compare against the previous step", exit = 2, text = "CIRCLE"),
            step("Ramp left", Symbol.RAMP_LEFT, 500, text = "RAMP"),
            step("U-turn", Symbol.U_TURN, 200, text = "U TURN"),
            step("Arrive", Symbol.ARRIVE, 100, note = "Steady arrival", text = "DESTINATION", totalM = 100),
            step("Arrive blinking", Symbol.ARRIVE, 20, note = "Arrival uses H, not lowercase g", text = "DESTINATION", totalM = 20),
        ),
    )

    /**
     * Every letter A-Z. One pass produces the definitive icon table for a specific
     * cluster, which is strictly better information than anything in the APK.
     */
    val SYMBOL_SWEEP = SequenceScript(
        id = "symbol_sweep",
        name = "Symbol sweep A-Z",
        description = "Sends every letter in turn. Record what each one draws.",
        defaultDwellMs = 1_500L,
        steps = ('A'..'Z').map { letter ->
            SequenceStep(
                label = letter.toString(),
                note = dev.jay.betterconnect.core.model.SymbolCatalog.labelFor(letter),
                nav = NavState(
                    symbol = Symbol.STRAIGHT,
                    symbolOverride = letter.code,
                    distanceToTurnM = 500,
                    distanceLeftM = 5_000,
                    etaSeconds = 600,
                    text = "SYMBOL $letter",
                ),
            )
        },
    )

    /** Walks the metre/kilometre switch, including the 999 m boundary defect. */
    val DISTANCE_SWEEP = SequenceScript(
        id = "distance_sweep",
        name = "Distance boundary",
        description = "990 m to 1010 m. Watch the unit flip; 999 m should read 1.00 km.",
        defaultDwellMs = 1_200L,
        steps = (990..1010).map { metres ->
            SequenceStep(
                label = "$metres m",
                note = if (metres == 999) "The off-by-one: expect 1.00 km" else null,
                nav = NavState(
                    symbol = Symbol.LEFT,
                    distanceToTurnM = metres,
                    distanceLeftM = metres * 10,
                    etaSeconds = 600,
                    text = "DIST $metres",
                ),
            )
        },
    )

    /** Finds the cluster's real text capacity, which the APK caps at 31 without testing. */
    val TEXT_SWEEP = SequenceScript(
        id = "text_sweep",
        name = "Text length",
        description = "1 to 31 characters. Find where the cluster truncates or scrolls.",
        defaultDwellMs = 1_000L,
        steps = (1..31).map { len ->
            SequenceStep(
                label = "$len chars",
                nav = NavState(
                    symbol = Symbol.STRAIGHT,
                    distanceToTurnM = 500,
                    distanceLeftM = 5_000,
                    etaSeconds = 600,
                    text = (0 until len).map { 'A' + (it % 26) }.joinToString(""),
                ),
            )
        },
    )

    /** The exit number lives in the high nibble of byte 7, shared with the ETA hour. */
    val ROUNDABOUT_EXITS = SequenceScript(
        id = "roundabout_exits",
        name = "Roundabout exits",
        description = "Exits 1-7 in both families. Confirms the nibble and the numbering direction.",
        steps = (1..7).flatMap { exit ->
            listOf(Symbol.ROUNDABOUT, Symbol.ROUNDABOUT_ALT).map { symbol ->
                SequenceStep(
                    label = "${symbol.letter} exit $exit",
                    nav = NavState(
                        symbol = symbol,
                        distanceToTurnM = 300,
                        distanceLeftM = 3_000,
                        etaSeconds = 600,
                        text = "EXIT $exit",
                        roundaboutExit = exit,
                    ),
                )
            }
        },
    )

    val all = listOf(ROUTE_WALK, SYMBOL_SWEEP, DISTANCE_SWEEP, TEXT_SWEEP, ROUNDABOUT_EXITS)

    fun byId(id: String): SequenceScript = all.first { it.id == id }
}

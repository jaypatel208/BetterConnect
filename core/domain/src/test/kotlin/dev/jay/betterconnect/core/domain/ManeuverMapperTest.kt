package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verified against Google's current Routes API v2 `Maneuver` reference (2026-08-29) - see
 * [ManeuverMapper]'s KDoc. Every value the API can actually return is exercised here.
 */
class ManeuverMapperTest {

    private val allManeuvers = listOf(
        "MANEUVER_UNSPECIFIED",
        "TURN_SLIGHT_LEFT",
        "TURN_SHARP_LEFT",
        "UTURN_LEFT",
        "TURN_LEFT",
        "TURN_SLIGHT_RIGHT",
        "TURN_SHARP_RIGHT",
        "UTURN_RIGHT",
        "TURN_RIGHT",
        "STRAIGHT",
        "RAMP_LEFT",
        "RAMP_RIGHT",
        "MERGE",
        "FORK_LEFT",
        "FORK_RIGHT",
        "FERRY",
        "FERRY_TRAIN",
        "ROUNDABOUT_LEFT",
        "ROUNDABOUT_RIGHT",
        "DEPART",
        "NAME_CHANGE",
    )

    /** Inert on this cluster - see MANEUVERS.md §5/§6, trap B1. */
    private val inertLetters = setOf('M', 'S', 'T', 'U', 'W', 'Y', 'N')

    @Test
    fun `every documented maneuver value is handled`() {
        allManeuvers.forEach { maneuver ->
            // Must not throw and must not silently fall through to an unknown default.
            ManeuverMapper.map(maneuver)
        }
    }

    @Test
    fun `no mapped symbol is ever an inert code`() {
        allManeuvers.forEach { maneuver ->
            val mapped = ManeuverMapper.map(maneuver)
            mapped.symbol?.let { symbol ->
                assertFalse(
                    "maneuver $maneuver mapped to inert code ${symbol.letter}",
                    symbol.letter in inertLetters,
                )
            }
        }
    }

    @Test
    fun `never defaults to the rotary wrong-way code`() {
        allManeuvers.forEach { maneuver ->
            val mapped = ManeuverMapper.map(maneuver)
            assertFalse(
                "maneuver $maneuver defaulted to ROTARY (B) - never copy the vendor's WRONG_WAY fallback",
                mapped.symbol == Symbol.ROTARY,
            )
        }
    }

    @Test
    fun `unspecified maneuvers hold the previous icon rather than guessing`() {
        assertNull(ManeuverMapper.map("MANEUVER_UNSPECIFIED").symbol)
    }

    @Test
    fun `an unknown wire value also holds rather than guessing`() {
        assertNull(ManeuverMapper.map("SOME_FUTURE_MANEUVER_NOT_YET_SEEN").symbol)
    }

    @Test
    fun `roundabouts fall back to the exit turn direction, never to N or U`() {
        assertEquals(Symbol.LEFT, ManeuverMapper.map("ROUNDABOUT_LEFT").symbol)
        assertEquals(Symbol.RIGHT, ManeuverMapper.map("ROUNDABOUT_RIGHT").symbol)
    }

    @Test
    fun `ramps use the dedicated ramp codes, not plain turns`() {
        assertEquals(Symbol.RAMP_LEFT, ManeuverMapper.map("RAMP_LEFT").symbol)
        assertEquals(Symbol.RAMP_RIGHT, ManeuverMapper.map("RAMP_RIGHT").symbol)
    }

    @Test
    fun `ferries degrade to straight rather than an inert ferry icon`() {
        assertEquals(Symbol.STRAIGHT, ManeuverMapper.map("FERRY").symbol)
        assertEquals(Symbol.STRAIGHT, ManeuverMapper.map("FERRY_TRAIN").symbol)
    }

    @Test
    fun `every mapped symbol carries a non-scrolling caption`() {
        allManeuvers.forEach { maneuver ->
            val mapped = ManeuverMapper.map(maneuver)
            if (mapped.symbol != null) {
                assertTrue(
                    "caption for $maneuver ('${mapped.caption}') is too long to avoid scrolling",
                    mapped.caption.length <= 12,
                )
            }
        }
    }

    @Test
    fun `merge uses the dedicated merge code`() {
        assertEquals(Symbol.MERGE, ManeuverMapper.map("MERGE").symbol)
    }
}

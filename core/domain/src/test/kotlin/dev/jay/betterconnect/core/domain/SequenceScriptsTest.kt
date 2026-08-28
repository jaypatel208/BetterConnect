package dev.jay.betterconnect.core.domain

import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.DecodeResult
import dev.jay.betterconnect.core.protocol.TbtEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceScriptsTest {

    private val encoder = TbtEncoder()

    @Test
    fun `every step of every script encodes to a valid frame`() {
        SequenceScripts.all.forEach { script ->
            script.steps.forEach { step ->
                val bytes = encoder.encode(step.nav)
                assertEquals("${script.id}/${step.label} size", ClusterProtocol.TBT_SIZE, bytes.size)
                assertTrue(
                    "${script.id}/${step.label} did not decode cleanly",
                    dev.jay.betterconnect.core.protocol.TbtDecoder.decode(bytes) is DecodeResult.Valid,
                )
            }
        }
    }

    @Test
    fun `script ids are unique so they can be persisted and restored`() {
        val ids = SequenceScripts.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `no script is empty`() {
        SequenceScripts.all.forEach { assertTrue(it.name, it.steps.isNotEmpty()) }
    }

    /**
     * The whole reason this script exists: the APK cannot tell us which roundabout family
     * a cluster renders, so the two must appear consecutively to be compared by eye.
     */
    @Test
    fun `route walk places the two roundabout families back to back`() {
        val symbols = SequenceScripts.ROUTE_WALK.steps.map { it.nav.symbol }
        val uIndex = symbols.indexOf(Symbol.ROUNDABOUT)
        val nIndex = symbols.indexOf(Symbol.ROUNDABOUT_ALT)

        assertTrue("both families must be present", uIndex >= 0 && nIndex >= 0)
        assertEquals("they must be adjacent to be comparable", 1, nIndex - uIndex)
    }

    @Test
    fun `route walk crosses the blink threshold in both directions`() {
        val distances = SequenceScripts.ROUTE_WALK.steps.map { it.nav.distanceToTurnM }
        assertTrue("needs a steady step", distances.any { it > NavState.BLINK_THRESHOLD_M })
        assertTrue("needs a blinking step", distances.any { it <= NavState.BLINK_THRESHOLD_M })
    }

    @Test
    fun `symbol sweep covers every letter exactly once`() {
        val codes = SequenceScripts.SYMBOL_SWEEP.steps.map { it.nav.symbolCode }
        assertEquals(26, codes.size)
        assertEquals(('A'..'Z').map { it.code }, codes)
    }

    @Test
    fun `symbol sweep bypasses the enum so undocumented codes can be sent`() {
        val yStep = SequenceScripts.SYMBOL_SWEEP.steps.first { it.label == "Y" }
        assertEquals('Y'.code, yStep.nav.symbolCode)
        assertNotNull("the sweep must flag what it does not know", yStep.note)
    }

    @Test
    fun `distance sweep brackets the 999 metre boundary and labels it`() {
        val step = SequenceScripts.DISTANCE_SWEEP.steps.first { it.nav.distanceToTurnM == 999 }
        assertNotNull("the off-by-one should be called out to the tester", step.note)

        val decoded = decodeStep(step)
        assertEquals("999 m must render as 1.00 km", false, decoded.turn.isMetres)
        assertEquals(1, decoded.turn.whole)
    }

    @Test
    fun `roundabout exit sweep sets the high nibble for every exit`() {
        SequenceScripts.ROUNDABOUT_EXITS.steps.forEach { step ->
            assertEquals(step.label, step.nav.roundaboutExit, decodeStep(step).roundaboutExit)
        }
    }

    @Test
    fun `text sweep never exceeds the wire limit`() {
        SequenceScripts.TEXT_SWEEP.steps.forEach { step ->
            assertTrue(step.label, decodeStep(step).text.length <= ClusterProtocol.MAX_TEXT_LEN)
        }
    }

    private fun decodeStep(step: SequenceStep) = (
        dev.jay.betterconnect.core.protocol.TbtDecoder.decode(
            encoder.encode(step.nav),
        ) as DecodeResult.Valid
        ).frame
}

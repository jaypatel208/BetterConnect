package dev.jay.betterconnect.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolTest {

    @Test
    fun `blinking is the lowercase form for every symbol except arrival`() {
        Symbol.entries.filter { it != Symbol.ARRIVE }.forEach { symbol ->
            assertEquals(
                "${symbol.name} blink code",
                symbol.letter.code + 0x20,
                symbol.blinkCode,
            )
        }
    }

    /** The one entry in the original encoder that breaks the +0x20 rule. */
    @Test
    fun `arrival blinks with H rather than lowercase g`() {
        assertEquals('G'.code, Symbol.ARRIVE.steadyCode)
        assertEquals('H'.code, Symbol.ARRIVE.blinkCode)
    }

    @Test
    fun `straight and arrive share the steady code so only the blink form separates them`() {
        assertEquals(Symbol.STRAIGHT.steadyCode, Symbol.ARRIVE.steadyCode)
        assertTrue(Symbol.STRAIGHT.blinkCode != Symbol.ARRIVE.blinkCode)
    }

    @Test
    fun `every symbol code is a printable uppercase letter`() {
        Symbol.entries.forEach { symbol ->
            assertTrue("${symbol.name} = ${symbol.letter}", symbol.letter in 'A'..'Z')
        }
    }
}

class SymbolCatalogTest {

    @Test
    fun `the sweep covers the whole alphabet`() {
        assertEquals(('A'..'Z').toList(), SymbolCatalog.sweepLetters)
    }

    @Test
    fun `lookup is case insensitive so blink codes resolve too`() {
        assertEquals(SymbolCatalog.labelFor('I'), SymbolCatalog.labelFor('i'))
    }

    /**
     * Y is the one code the decompiled app could not explain. It must stay marked unknown
     * so a sweep flags it rather than quietly showing a wrong label.
     */
    @Test
    fun `symbol Y is reported as unknown`() {
        assertEquals(SymbolCatalog.UNKNOWN_LABEL, SymbolCatalog.labelFor('Y'))
        assertFalse(SymbolCatalog.isDocumented('Y'))
    }

    @Test
    fun `documented codes report a real meaning`() {
        listOf('I', 'J', 'E', 'F', 'G', 'U', 'N', 'K', 'L').forEach { letter ->
            assertTrue("$letter should be documented", SymbolCatalog.isDocumented(letter))
        }
    }

    @Test
    fun `undocumented letters fall back to unknown rather than throwing`() {
        assertEquals(SymbolCatalog.UNKNOWN_LABEL, SymbolCatalog.labelFor('W'))
    }
}

class DeviceInfoTest {

    private fun device(name: String?) = DeviceInfo(
        address = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -60,
        bonded = false,
        connectable = true,
    )

    @Test
    fun `candidate matching mirrors the official app's name substrings`() {
        assertTrue(device("PULSAR N160").isCandidate)
        assertTrue(device("pulsar n160").isCandidate)
        assertTrue(device("Bajaj Dominar 400").isCandidate)
        assertTrue(device("Chetak Freedom").isCandidate)
    }

    @Test
    fun `unrelated devices are not candidates`() {
        assertFalse(device("Galaxy Buds").isCandidate)
        assertFalse(device(null).isCandidate)
        assertFalse(device("").isCandidate)
    }
}

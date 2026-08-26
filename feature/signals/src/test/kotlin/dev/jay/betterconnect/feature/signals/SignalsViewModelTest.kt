package dev.jay.betterconnect.feature.signals

import app.cash.turbine.test
import dev.jay.betterconnect.core.link.SendMode
import dev.jay.betterconnect.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `tapping a letter sends that exact byte as the icon`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('K'))

        assertEquals('K', h.transport.lastDecoded!!.symbolChar)
    }

    /** The sweep must be able to send codes with no documented meaning, Y included. */
    @Test
    fun `undocumented letters are sent unchanged`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('Y'))

        assertEquals('Y', h.transport.lastDecoded!!.symbolChar)
    }

    @Test
    fun `the blink toggle switches to the lowercase form and resends`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('I'))
        assertEquals('I', h.transport.lastDecoded!!.symbolChar)

        vm.onAction(SignalsAction.SetBlinking(true))
        assertEquals('i', h.transport.lastDecoded!!.symbolChar)
        assertTrue(h.transport.lastDecoded!!.blinking)
    }

    @Test
    fun `blink toggling before any letter is chosen sends nothing`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SetBlinking(true))

        assertTrue(h.transport.received.isEmpty())
    }

    @Test
    fun `changing a field resends the current letter with the new value`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('J'))
        vm.onAction(SignalsAction.UpdateConfig(SignalConfig(distanceToTurnM = 2_500, text = "CG ROAD")))

        val decoded = h.transport.lastDecoded!!
        assertEquals('J', decoded.symbolChar)
        assertEquals(false, decoded.turn.isMetres)
        assertEquals(2, decoded.turn.whole)
        assertEquals(50, decoded.turn.fraction)
        assertEquals("CG ROAD", decoded.text)
    }

    @Test
    fun `the roundabout exit reaches the shared nibble`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.UpdateConfig(SignalConfig(roundaboutExit = 5)))
        vm.onAction(SignalsAction.SendLetter('U'))

        assertEquals(5, h.transport.lastDecoded!!.roundaboutExit)
    }

    @Test
    fun `clear sends the all-zero frame and drops the selection`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('I'))
        vm.onAction(SignalsAction.Clear)

        assertTrue(h.transport.received.last().bytes.all { it == 0.toByte() })
        vm.uiState.test {
            awaitItem()
            runCurrent()
            assertEquals(null, expectMostRecentItem().selectedLetter)
        }
    }

    @Test
    fun `ui state reports the selected letter and its documented meaning`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)

        vm.uiState.test {
            awaitItem()
            vm.onAction(SignalsAction.SendLetter('I'))
            runCurrent()
            val state = expectMostRecentItem()
            assertEquals('I', state.selectedLetter)
            assertEquals("Turn left", state.selectedLabel)
            assertTrue(state.canSend)
        }
    }

    @Test
    fun `changed bytes are surfaced so the hex view can highlight them`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)

        vm.uiState.test {
            awaitItem()
            vm.onAction(SignalsAction.SendLetter('I'))
            runCurrent()
            vm.onAction(SignalsAction.SendLetter('J'))
            runCurrent()

            val state = expectMostRecentItem()
            // Only the icon byte and the checksum should differ between two letters.
            assertEquals(setOf(1, 47), state.changedBytes)
        }
    }

    @Test
    fun `switching to one shot stops the heartbeat repeating`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SetMode(SendMode.ONE_SHOT))
        vm.uiState.test {
            awaitItem()
            // uiState is stateIn(WhileSubscribed), so upstream only runs once subscribed.
            runCurrent()
            assertEquals(SendMode.ONE_SHOT, expectMostRecentItem().sendMode)
        }
    }

    @Test
    fun `nothing is written when the link is not ready`() = runTest {
        val h = TestHarness(backgroundScope, connected = false)
        val vm = SignalsViewModel(h.controller)
        runCurrent()

        vm.onAction(SignalsAction.SendLetter('I'))

        assertTrue(h.transport.received.isEmpty())
        vm.uiState.test {
            awaitItem()
            runCurrent()
            assertTrue(!expectMostRecentItem().canSend)
        }
    }
}

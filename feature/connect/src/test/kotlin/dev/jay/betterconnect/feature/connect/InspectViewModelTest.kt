package dev.jay.betterconnect.feature.connect

import app.cash.turbine.test
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.UnsupportedReason
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.MainDispatcherRule
import dev.jay.betterconnect.core.testing.TestData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The verdict is the whole point of the Inspect screen: it is the single line that decides
 * whether the trip to the bike can continue. Every branch is pinned.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspectViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(scope: CoroutineScope) {
        val transport = FakeClusterTransport(ConnectionState.Idle)
        private val encoder = TbtEncoder()
        private val scheduler = WriteScheduler(transport)
        val controller = ClusterController(
            transport = transport,
            scheduler = scheduler,
            runner = SequenceRunner(scheduler, encoder),
            encoder = encoder,
            scanner = FakeDeviceScanner(),
            log = DiagLog(),
            scope = scope,
        )
    }

    @Test
    fun `not connected reports no verdict`() = runTest {
        val h = Harness(backgroundScope)
        val vm = InspectViewModel(h.controller)
        vm.uiState.test {
            // Nothing changes while disconnected, so there is only the initial emission.
            assertEquals(Verdict.NOT_CONNECTED, awaitItem().verdict)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a healthy cluster is reported as supported`() = runTest {
        val h = Harness(backgroundScope)
        val vm = InspectViewModel(h.controller)
        vm.uiState.test {
            awaitItem()
            h.transport.setGattDump(TestData.healthyDump())
            h.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
            runCurrent()

            val state = expectMostRecentItem()
            assertEquals(Verdict.SUPPORTED, state.verdict)
            assertTrue(state.mtuAdequate)
            assertEquals(64, state.mtu)
        }
    }

    @Test
    fun `each unsupported reason maps to its own verdict`() = runTest {
        val expected = mapOf(
            UnsupportedReason.SERVICE_MISSING to Verdict.SERVICE_MISSING,
            UnsupportedReason.CHARACTERISTIC_MISSING to Verdict.CHARACTERISTIC_MISSING,
            UnsupportedReason.NOT_WRITABLE to Verdict.NOT_WRITABLE,
            UnsupportedReason.MTU_TOO_SMALL to Verdict.MTU_TOO_SMALL,
        )
        // Every reason must be covered, so a new one cannot be added without a verdict.
        assertEquals(UnsupportedReason.entries.toSet(), expected.keys)

        expected.forEach { (reason, verdict) ->
            val h = Harness(backgroundScope)
            val vm = InspectViewModel(h.controller)
            vm.uiState.test {
                awaitItem()
                h.transport.setState(ConnectionState.Unsupported(FakeClusterTransport.ADDRESS, reason))
                runCurrent()
                assertEquals("for $reason", verdict, expectMostRecentItem().verdict)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `an MTU below the frame size is flagged as inadequate`() = runTest {
        val h = Harness(backgroundScope)
        val vm = InspectViewModel(h.controller)
        vm.uiState.test {
            awaitItem()
            h.transport.setGattDump(TestData.healthyDump(mtu = 23))
            h.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 23))
            runCurrent()

            val state = expectMostRecentItem()
            assertFalse("23 is below the ${ClusterProtocol.MIN_MTU} minimum", state.mtuAdequate)
        }
    }

    @Test
    fun `the exported table names the TBT characteristic`() = runTest {
        val h = Harness(backgroundScope)
        val vm = InspectViewModel(h.controller)
        vm.uiState.test {
            awaitItem()
            h.transport.setGattDump(TestData.healthyDump())
            h.transport.setState(ConnectionState.Ready(FakeClusterTransport.ADDRESS, 64))
            runCurrent()
            expectMostRecentItem()
        }

        val text = vm.exportText()
        assertTrue(text.contains(ClusterProtocol.SERVICE_UUID.toString()))
        assertTrue(text.contains("TBT_INFO"))
        assertTrue(text.contains("MTU: 64"))
    }

    @Test
    fun `export explains itself when nothing is connected`() = runTest {
        val h = Harness(backgroundScope)
        val vm = InspectViewModel(h.controller)
        assertEquals("Not connected.", vm.exportText())
    }
}

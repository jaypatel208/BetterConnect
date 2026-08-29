package dev.jay.betterconnect.feature.devices

import app.cash.turbine.test
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.link.ControlPump
import dev.jay.betterconnect.core.link.GeneralScheduler
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceRepository
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(scope: CoroutineScope, lastAddress: String? = null) {
        val scanner = FakeDeviceScanner()
        val transport = FakeClusterTransport(ConnectionState.Idle)
        val repository = FakeDeviceRepository(lastAddress)
        private val encoder = TbtEncoder()
        private val scheduler = WriteScheduler(transport)
        private val controlPump = ControlPump(transport)
        private val generalScheduler = GeneralScheduler(transport, controlPump.acks)
        val controller = ClusterController(
            transport = transport,
            scheduler = scheduler,
            controlPump = controlPump,
            generalScheduler = generalScheduler,
            runner = SequenceRunner(scheduler, encoder),
            encoder = encoder,
            scanner = scanner,
            log = DiagLog(),
            scope = scope,
        )
    }

    @Test
    fun `candidates and others are partitioned by name`() = runTest {
        val h = Harness(backgroundScope)
        val vm = DevicesViewModel(h.controller, h.repository)
        vm.uiState.test {
            awaitItem()
            h.scanner.emit(listOf(FakeDeviceScanner.cluster, FakeDeviceScanner.unrelated))
            runCurrent()

            val state = expectMostRecentItem()
            assertEquals(listOf(FakeDeviceScanner.cluster), state.candidates)
            assertEquals(listOf(FakeDeviceScanner.unrelated), state.others)
        }
    }

    @Test
    fun `connecting persists the address`() = runTest {
        val h = Harness(backgroundScope)
        val vm = DevicesViewModel(h.controller, h.repository)
        vm.onAction(DevicesAction.Connect(FakeClusterTransport.ADDRESS))
        runCurrent()

        assertEquals(FakeClusterTransport.ADDRESS, h.repository.lastAddress.value)
    }

    @Test
    fun `connecting stops an in-progress scan`() = runTest {
        val h = Harness(backgroundScope)
        val vm = DevicesViewModel(h.controller, h.repository)
        h.scanner.start()
        assertTrue(h.scanner.scanning.value)

        vm.onAction(DevicesAction.Connect(FakeClusterTransport.ADDRESS))
        runCurrent()

        assertFalse(h.scanner.scanning.value)
    }

    @Test
    fun `toggling scan starts and stops it`() = runTest {
        val h = Harness(backgroundScope)
        val vm = DevicesViewModel(h.controller, h.repository)
        vm.onAction(DevicesAction.ToggleScan)
        assertTrue(h.scanner.scanning.value)

        vm.onAction(DevicesAction.ToggleScan)
        assertFalse(h.scanner.scanning.value)
    }

    @Test
    fun `startScanningIfIdle does not stop an already-running scan`() = runTest {
        val h = Harness(backgroundScope)
        val vm = DevicesViewModel(h.controller, h.repository)
        h.scanner.start()
        val startsBefore = h.scanner.startCalls

        vm.startScanningIfIdle()

        assertEquals(startsBefore, h.scanner.startCalls)
        assertTrue(h.scanner.scanning.value)
    }

    @Test
    fun `the last connected address surfaces in state for reconnect UI`() = runTest {
        val h = Harness(backgroundScope, lastAddress = FakeClusterTransport.ADDRESS)
        val vm = DevicesViewModel(h.controller, h.repository)
        vm.uiState.test {
            awaitItem()
            runCurrent()
            assertEquals(FakeClusterTransport.ADDRESS, expectMostRecentItem().lastAddress)
        }
    }
}

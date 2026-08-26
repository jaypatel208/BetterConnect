package dev.jay.betterconnect.feature.signals

import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.domain.DiagLog
import dev.jay.betterconnect.core.domain.SequenceRunner
import dev.jay.betterconnect.core.link.WriteScheduler
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.protocol.TbtEncoder
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import dev.jay.betterconnect.core.testing.FakeDeviceScanner
import dev.jay.betterconnect.core.testing.TestClocks
import kotlinx.coroutines.CoroutineScope

/** Real controller, real scheduler, real encoder - only the radio is fake. */
class TestHarness(scope: CoroutineScope, connected: Boolean = true) {
    val transport = FakeClusterTransport(
        if (connected) {
            ConnectionState.Ready(FakeClusterTransport.ADDRESS, FakeClusterTransport.DEFAULT_MTU)
        } else {
            ConnectionState.Idle
        },
    )
    private val encoder = TbtEncoder(clock = TestClocks.TEN_AM)
    private val scheduler = WriteScheduler(transport)
    val log = DiagLog()
    val controller = ClusterController(
        transport = transport,
        scheduler = scheduler,
        runner = SequenceRunner(scheduler, encoder),
        encoder = encoder,
        scanner = FakeDeviceScanner(),
        log = log,
        scope = scope,
    )
}

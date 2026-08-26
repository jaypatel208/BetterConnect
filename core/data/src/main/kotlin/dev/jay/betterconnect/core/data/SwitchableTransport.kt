package dev.jay.betterconnect.core.data

import dev.jay.betterconnect.core.ble.BleClusterTransport
import dev.jay.betterconnect.core.link.ClusterTransport
import dev.jay.betterconnect.core.link.DemoCapableTransport
import dev.jay.betterconnect.core.link.WriteOutcome
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.protocol.TbtFrame
import dev.jay.betterconnect.core.testing.FakeClusterTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes every call to either the radio or an in-process fake cluster.
 *
 * Demo mode exists so the entire app - every screen, the sequence runner, the virtual
 * cluster, the log - can be exercised with no bike present. It is the same fake the tests
 * use, so what you drive by hand and what CI asserts are the same object.
 */
@Singleton
class SwitchableTransport @Inject constructor(
    private val real: BleClusterTransport,
    scope: CoroutineScope,
) : DemoCapableTransport {

    val fake = FakeClusterTransport(ConnectionState.Idle)

    private val _demoMode = MutableStateFlow(false)
    override val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val active: ClusterTransport get() = if (_demoMode.value) fake else real

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val state: StateFlow<ConnectionState> =
        _demoMode.flatMapLatest { demo -> if (demo) fake.state else real.state }
            .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val gattDump: StateFlow<GattDump?> =
        _demoMode.flatMapLatest { demo -> if (demo) fake.gattDump else real.gattDump }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setDemoMode(enabled: Boolean) {
        if (_demoMode.value == enabled) return
        active.disconnect()
        _demoMode.value = enabled
        fake.setDemoMode(enabled)
    }

    override fun connect(address: String) = active.connect(address)

    override fun disconnect() = active.disconnect()

    override fun write(frame: TbtFrame): WriteOutcome = active.write(frame)
}

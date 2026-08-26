package dev.jay.betterconnect.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.link.WriteStats
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.GattDump
import dev.jay.betterconnect.core.protocol.ClusterProtocol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The one line that decides whether this project can proceed at all. */
enum class Verdict(val headline: String, val detail: String) {
    NOT_CONNECTED("Not connected", "Connect to a device to inspect its GATT table."),
    SUPPORTED(
        "TBT_INFO present and writable",
        "This cluster speaks the protocol. Go to Signals and send a symbol.",
    ),
    SERVICE_MISSING(
        "Cluster service not present",
        "This device does not expose the vendor service. Nothing downstream applies.",
    ),
    CHARACTERISTIC_MISSING(
        "TBT_INFO characteristic missing",
        "The service is there but the navigation characteristic is not.",
    ),
    NOT_WRITABLE(
        "TBT_INFO is not writable",
        "The characteristic exists but exposes no write property.",
    ),
    MTU_TOO_SMALL(
        "Negotiated MTU is too small",
        "A 48-byte frame needs MTU >= ${ClusterProtocol.MIN_MTU}. Writes cannot succeed.",
    ),
}

data class InspectUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val dump: GattDump? = null,
    val stats: WriteStats = WriteStats(),
    val verdict: Verdict = Verdict.NOT_CONNECTED,
) {
    val mtu: Int? get() = dump?.mtu?.takeIf { it > 0 }
    val mtuAdequate: Boolean get() = (mtu ?: 0) >= ClusterProtocol.MIN_MTU
}

@HiltViewModel
class InspectViewModel @Inject constructor(
    private val controller: ClusterController,
) : ViewModel() {

    val uiState: StateFlow<InspectUiState> = combine(
        controller.state,
        controller.gattDump,
        controller.stats,
    ) { connection, dump, stats ->
        InspectUiState(
            connection = connection,
            dump = dump,
            stats = stats,
            verdict = verdictOf(connection, dump),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InspectUiState())

    fun resetStats() = controller.resetStats()

    fun exportText(): String {
        val state = uiState.value
        val dump = state.dump ?: return "Not connected."
        return buildString {
            appendLine("GATT table for ${dump.address}")
            appendLine("MTU: ${dump.mtu}")
            appendLine("Verdict: ${state.verdict.headline}")
            appendLine()
            dump.services.forEach { service ->
                appendLine("service ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    appendLine(
                        "   char ${characteristic.uuid}  [${characteristic.properties.joinToString(" ")}]" +
                            if (characteristic.isTbtInfo) "   <-- TBT_INFO" else "",
                    )
                }
            }
        }
    }

    private fun verdictOf(connection: ConnectionState, dump: GattDump?): Verdict = when {
        connection is ConnectionState.Unsupported -> when (connection.reason) {
            dev.jay.betterconnect.core.model.UnsupportedReason.SERVICE_MISSING -> Verdict.SERVICE_MISSING
            dev.jay.betterconnect.core.model.UnsupportedReason.CHARACTERISTIC_MISSING -> Verdict.CHARACTERISTIC_MISSING
            dev.jay.betterconnect.core.model.UnsupportedReason.NOT_WRITABLE -> Verdict.NOT_WRITABLE
            dev.jay.betterconnect.core.model.UnsupportedReason.MTU_TOO_SMALL -> Verdict.MTU_TOO_SMALL
        }
        connection is ConnectionState.Ready && dump?.hasTbtCharacteristic == true -> Verdict.SUPPORTED
        else -> Verdict.NOT_CONNECTED
    }
}

package dev.jay.betterconnect.feature.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.link.SendMode
import dev.jay.betterconnect.core.link.WriteStats
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.model.NavState
import dev.jay.betterconnect.core.model.Symbol
import dev.jay.betterconnect.core.model.SymbolCatalog
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Everything the sweep needs to hold steady while only the symbol byte changes. */
data class SignalConfig(
    val distanceToTurnM: Int = 500,
    val distanceLeftM: Int = 8_400,
    val text: String = "TEST ROAD",
    val roundaboutExit: Int = 0,
    val gpsActive: Boolean = true,
)

data class SignalsUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val config: SignalConfig = SignalConfig(),
    val selectedLetter: Char? = null,
    val blinking: Boolean = false,
    val sendMode: SendMode = SendMode.HEARTBEAT,
    val currentFrame: TbtFrame? = null,
    val previousFrame: TbtFrame? = null,
    val stats: WriteStats = WriteStats(),
) {
    val canSend: Boolean get() = connection is ConnectionState.Ready
    val changedBytes: Set<Int> get() = currentFrame?.diffIndices(previousFrame).orEmpty()
    val selectedLabel: String? get() = selectedLetter?.let(SymbolCatalog::labelFor)
}

sealed interface SignalsAction {
    data class SendLetter(val letter: Char) : SignalsAction
    data class SetBlinking(val blinking: Boolean) : SignalsAction
    data class SetMode(val mode: SendMode) : SignalsAction
    data class UpdateConfig(val config: SignalConfig) : SignalsAction
    data object Clear : SignalsAction
}

@HiltViewModel
class SignalsViewModel @Inject constructor(private val controller: ClusterController) : ViewModel() {

    private val config = MutableStateFlow(SignalConfig())
    private val selected = MutableStateFlow<Char?>(null)
    private val blinking = MutableStateFlow(false)
    private val previous = MutableStateFlow<TbtFrame?>(null)

    val uiState: StateFlow<SignalsUiState> = combine(
        controller.state,
        controller.currentFrame,
        controller.sendMode,
        controller.stats,
        combine(config, selected, blinking, previous) { c, s, b, p -> Quad(c, s, b, p) },
    ) { connection, frame, mode, stats, local ->
        SignalsUiState(
            connection = connection,
            config = local.config,
            selectedLetter = local.selected,
            blinking = local.blinking,
            sendMode = mode,
            currentFrame = frame,
            previousFrame = local.previous,
            stats = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalsUiState())

    fun onAction(action: SignalsAction) {
        when (action) {
            is SignalsAction.SendLetter -> send(action.letter)
            is SignalsAction.SetBlinking -> {
                blinking.value = action.blinking
                selected.value?.let(::send)
            }
            is SignalsAction.SetMode -> controller.setSendMode(action.mode)
            is SignalsAction.UpdateConfig -> {
                config.value = action.config
                selected.value?.let(::send)
            }
            SignalsAction.Clear -> {
                selected.value = null
                previous.value = null
                controller.clearCluster()
            }
        }
    }

    private fun send(letter: Char) {
        previous.update { controller.currentFrame.value }
        selected.value = letter
        val code = if (blinking.value) letter.lowercaseChar().code else letter.uppercaseChar().code
        val c = config.value
        controller.send(
            NavState(
                // Overridden below; the enum is irrelevant when a raw code is supplied.
                symbol = Symbol.STRAIGHT,
                symbolOverride = code,
                distanceToTurnM = c.distanceToTurnM,
                distanceLeftM = c.distanceLeftM,
                etaSeconds = 15 * 60,
                text = c.text,
                roundaboutExit = c.roundaboutExit,
                gpsActive = c.gpsActive,
            ),
        )
    }

    private data class Quad(
        val config: SignalConfig,
        val selected: Char?,
        val blinking: Boolean,
        val previous: TbtFrame?,
    )
}

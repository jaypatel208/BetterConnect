package dev.jay.betterconnect.feature.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.domain.SequenceProgress
import dev.jay.betterconnect.core.domain.SequenceScript
import dev.jay.betterconnect.core.domain.SequenceScripts
import dev.jay.betterconnect.core.model.ConnectionState
import dev.jay.betterconnect.core.protocol.TbtFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SequenceUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val scripts: List<SequenceScript> = SequenceScripts.all,
    val selectedId: String = SequenceScripts.ROUTE_WALK.id,
    val dwellMs: Long = SequenceScripts.ROUTE_WALK.defaultDwellMs,
    val loop: Boolean = false,
    val progress: SequenceProgress? = null,
    val currentFrame: TbtFrame? = null,
) {
    val selected: SequenceScript get() = scripts.first { it.id == selectedId }
    val running: Boolean get() = progress != null
    val canSend: Boolean get() = connection is ConnectionState.Ready
}

sealed interface SequenceAction {
    data class Select(val id: String) : SequenceAction
    data class SetDwell(val ms: Long) : SequenceAction
    data class SetLoop(val loop: Boolean) : SequenceAction
    data object Start : SequenceAction
    data object Stop : SequenceAction
}

@HiltViewModel
class SequenceViewModel @Inject constructor(
    private val controller: ClusterController,
) : ViewModel() {

    private val selectedId = MutableStateFlow(SequenceScripts.ROUTE_WALK.id)
    private val dwell = MutableStateFlow(SequenceScripts.ROUTE_WALK.defaultDwellMs)
    private val loop = MutableStateFlow(false)

    val uiState: StateFlow<SequenceUiState> = combine(
        controller.state,
        controller.sequenceProgress,
        controller.currentFrame,
        selectedId,
        combine(dwell, loop) { d, l -> d to l },
    ) { connection, progress, frame, id, (dwellMs, looping) ->
        SequenceUiState(
            connection = connection,
            selectedId = id,
            dwellMs = dwellMs,
            loop = looping,
            progress = progress,
            currentFrame = frame,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SequenceUiState())

    fun onAction(action: SequenceAction) {
        when (action) {
            is SequenceAction.Select -> {
                selectedId.value = action.id
                dwell.value = SequenceScripts.byId(action.id).defaultDwellMs
            }
            is SequenceAction.SetDwell -> dwell.value = action.ms
            is SequenceAction.SetLoop -> loop.value = action.loop
            SequenceAction.Start -> controller.startSequence(
                SequenceScripts.byId(selectedId.value),
                dwell.value,
                loop.value,
            )
            SequenceAction.Stop -> controller.stopSequence()
        }
    }
}

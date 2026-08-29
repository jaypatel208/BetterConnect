package dev.jay.betterconnect.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.data.RideLog
import dev.jay.betterconnect.core.domain.LogEntry
import dev.jay.betterconnect.core.model.GeneralVersion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

data class DebugUiState(
    val controlPumpEnabled: Boolean = true,
    val generalSchedulerEnabled: Boolean = true,
    val generalVersion: GeneralVersion = GeneralVersion.V1,
    val entries: ImmutableList<LogEntry> = kotlinx.collections.immutable.persistentListOf(),
) {
    val recent: List<LogEntry> get() = entries.takeLast(200).asReversed()
}

sealed interface DebugAction {
    data class SetControlPumpEnabled(val enabled: Boolean) : DebugAction
    data class SetGeneralSchedulerEnabled(val enabled: Boolean) : DebugAction
    data class SetGeneralVersion(val version: GeneralVersion) : DebugAction
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val controller: ClusterController,
    private val rideLog: RideLog,
) : ViewModel() {

    val uiState: StateFlow<DebugUiState> = combine(
        controller.controlPumpEnabled,
        controller.generalSchedulerEnabled,
        controller.generalVersion,
        controller.log.entries,
    ) { control, general, version, entries ->
        DebugUiState(control, general, version, entries.toImmutableList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState())

    /** The whole-ride file behind the 500-entry in-memory ring - what Export actually shares. */
    val rideLogFile: File get() = rideLog.file

    fun onAction(action: DebugAction) {
        when (action) {
            is DebugAction.SetControlPumpEnabled -> controller.setControlPumpEnabled(action.enabled)
            is DebugAction.SetGeneralSchedulerEnabled -> controller.setGeneralSchedulerEnabled(action.enabled)
            is DebugAction.SetGeneralVersion -> controller.setGeneralVersion(action.version)
        }
    }
}

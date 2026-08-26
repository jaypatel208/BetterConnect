package dev.jay.betterconnect.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jay.betterconnect.core.data.ClusterController
import dev.jay.betterconnect.core.domain.LogEntry
import dev.jay.betterconnect.core.domain.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LogUiState(
    val entries: List<LogEntry> = emptyList(),
    val activeFilters: Set<LogLevel> = LogLevel.entries.toSet(),
) {
    val visible: List<LogEntry> get() = entries.filter { it.level in activeFilters }
}

@HiltViewModel
class LogViewModel @Inject constructor(
    private val controller: ClusterController,
) : ViewModel() {

    private val filters = MutableStateFlow(LogLevel.entries.toSet())

    val uiState: StateFlow<LogUiState> = combine(
        controller.log.entries,
        filters,
    ) { entries, active ->
        LogUiState(entries, active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    fun toggleFilter(level: LogLevel) {
        filters.value = filters.value.let { if (level in it) it - level else it + level }
    }

    fun clear() = controller.log.clear()

    fun export(): String = controller.log.export()
}

package com.zenlauncher.zen.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.repository.SessionRepository
import com.zenlauncher.zen.domain.stats.StatsCalculator
import com.zenlauncher.zen.domain.stats.ZenStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    private val sessions: SessionRepository,
) : ViewModel() {

    val state: StateFlow<ZenStats> = sessions.observeAll()
        .map(StatsCalculator::from)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ZenStats.Empty,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

package com.vorynxp.usbguardian.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorynxp.usbguardian.data.db.LogDao
import com.vorynxp.usbguardian.data.db.LogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogFilter {
    ALL, BLOCKED, ALLOWED
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logDao: LogDao
) : ViewModel() {

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter

    val logsState: StateFlow<List<LogEntity>> = combine(
        logDao.getAllLogsFlow(),
        _filter
    ) { logs, currentFilter ->
        when (currentFilter) {
            LogFilter.ALL -> logs
            LogFilter.BLOCKED -> logs.filter { it.action.contains("Block", ignoreCase = true) }
            LogFilter.ALLOWED -> logs.filter { it.action.contains("Allow", ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilter(newFilter: LogFilter) {
        _filter.value = newFilter
    }

    fun clearLogs() {
        viewModelScope.launch {
            logDao.clearLogs()
        }
    }
}

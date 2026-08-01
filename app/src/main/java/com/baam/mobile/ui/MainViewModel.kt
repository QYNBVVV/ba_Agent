package com.baam.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baam.mobile.data.LogBus
import com.baam.mobile.engine.task.Task
import com.baam.mobile.engine.task.TaskProvider
import com.baam.mobile.safety.SafetyController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val taskProvider: TaskProvider,
    private val logBus: LogBus,
    private val safety: SafetyController,
) : ViewModel() {

    val tasks: List<Task> = taskProvider.all()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    val stopRequested: StateFlow<Boolean> = safety.stopRequested

    init {
        // 订阅日志总线，累积到列表供 UI 显示
        viewModelScope.launch {
            logBus.flow.collect { line ->
                _logs.value = (_logs.value + line).takeLast(MAX_LOG_LINES)
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    companion object {
        private const val MAX_LOG_LINES = 500
    }
}

package com.baam.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baam.mobile.data.LogBus
import com.baam.mobile.data.TaskConfigStore
import com.baam.mobile.engine.task.Task
import com.baam.mobile.engine.task.TaskBatchRunner
import com.baam.mobile.engine.task.TaskProvider
import com.baam.mobile.safety.SafetyController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val taskProvider: TaskProvider,
    private val logBus: LogBus,
    private val safety: SafetyController,
    private val configStore: TaskConfigStore,
    val batchRunner: TaskBatchRunner,
) : ViewModel() {

    val tasks: List<Task> = taskProvider.all()

    /** 启用的任务 id 集合（持久化） */
    val enabledTaskIds: StateFlow<Set<String>> =
        configStore.enabledTaskIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** 批次执行状态 */
    val batchState: StateFlow<TaskBatchRunner.BatchState> = batchRunner.state

    val stopRequested: StateFlow<Boolean> = safety.stopRequested

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        viewModelScope.launch {
            logBus.flow.collect { line ->
                _logs.value = (_logs.value + line).takeLast(MAX_LOG_LINES)
            }
        }
    }

    fun toggleTask(taskId: String, enabled: Boolean) {
        viewModelScope.launch { configStore.setTaskEnabled(taskId, enabled) }
    }

    /** 一键执行所有启用的任务 */
    fun isBatchRunning(): Boolean = batchState.value is TaskBatchRunner.BatchState.Running

    fun clearLogs() {
        _logs.value = emptyList()
    }

    companion object {
        private const val MAX_LOG_LINES = 500
    }
}

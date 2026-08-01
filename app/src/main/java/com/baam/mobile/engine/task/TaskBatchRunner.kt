package com.baam.mobile.engine.task

import com.baam.mobile.data.LogBus
import com.baam.mobile.data.history.HistoryRecorder
import com.baam.mobile.safety.SafetyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务批次执行结果。
 */
data class BatchRunResult(
    val taskId: String,
    val result: TaskResult,
)

/**
 * 多任务串行执行器：按顺序跑一组任务，每个任务独立护栏，单任务失败不影响后续。
 *
 * 用途：用户在 UI 勾选若干日常任务（咖啡厅/邮件/任务奖励…），点「一键执行」，
 * 由本类串行调度。前台服务持有其协程，SafetyController 任意时刻可全局停止。
 *
 * 状态通过 [state] 暴露给 UI：当前任务/进度/是否运行中。
 * 每次执行结果通过 [HistoryRecorder] 落盘，UI 可回看历史。
 */
@Singleton
class TaskBatchRunner @Inject constructor(
    private val taskRunner: TaskRunner,
    private val safety: SafetyController,
    private val logBus: LogBus,
    private val history: HistoryRecorder,
) {

    private val _state = MutableStateFlow<BatchState>(BatchState.Idle)
    val state: StateFlow<BatchState> = _state.asStateFlow()

    /**
     * 串行执行多个任务（带恢复重试）。
     * @param taskIds 待执行任务 id 列表（顺序执行）
     * @param ctxFactory TaskContext 工厂
     * @param recoveryEnabled 是否对失败任务做恢复重试
     * @return 每个任务的执行结果
     */
    suspend fun runBatch(
        taskIds: List<String>,
        taskProvider: TaskProvider,
        ctxFactory: () -> TaskContext,
        recoveryEnabled: Boolean = true,
    ): List<BatchRunResult> {
        if (taskIds.isEmpty()) {
            logBus.emit("Batch", "无任务可执行")
            return emptyList()
        }

        _state.value = BatchState.Running(total = taskIds.size, current = 0, currentTaskId = taskIds.first())
        val batchId = history.newBatchId()
        logBus.emit("Batch", "开始批次执行，共 ${taskIds.size} 个任务 (batch=$batchId)")

        val results = mutableListOf<BatchRunResult>()
        taskIds.forEachIndexed { index, taskId ->
            // 任意时刻可被 SafetyController 停止
            if (safety.isStopRequested) {
                logBus.emit("Batch", "收到停止信号，中止后续任务")
                results.add(BatchRunResult(taskId, TaskResult.Stopped))
                return@forEachIndexed
            }

            _state.value = BatchState.Running(
                total = taskIds.size,
                current = index,
                currentTaskId = taskId,
            )

            val task = taskProvider.get(taskId)
            logBus.emit("Batch", "▶ [${index + 1}/${taskIds.size}] ${task.displayName}")

            val startTime = System.currentTimeMillis()
            val result = if (recoveryEnabled) {
                taskRunner.runWithRecovery(task, ctxFactory)
            } else {
                taskRunner.run(task, ctxFactory)
            }
            val endTime = System.currentTimeMillis()
            results.add(BatchRunResult(taskId, result))
            logBus.emit("Batch", "✓ [${index + 1}/${taskIds.size}] ${task.displayName} → $result (${endTime - startTime}ms)")

            // 落盘历史
            try {
                history.record(taskId, task.displayName, result, startTime, endTime, batchId)
            } catch (e: Exception) {
                Timber.w(e, "记录运行历史失败")
            }

            // 任务间间隔，让游戏/系统喘口气
            kotlinx.coroutines.delay(1500)
        }

        _state.value = BatchState.Idle
        logBus.emit("Batch", "批次执行结束")
        return results
    }

    /** 请求中止（实际停止由 SafetyController 触发） */
    fun requestStop() {
        safety.requestStop()
        logBus.emit("Batch", "请求中止批次")
    }

    /** 批次执行状态 */
    sealed class BatchState {
        data object Idle : BatchState()
        data class Running(
            val total: Int,
            val current: Int,
            val currentTaskId: String,
        ) : BatchState()
    }
}

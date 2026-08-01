package com.baam.mobile.data.history

import com.baam.mobile.engine.task.TaskResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运行历史写入器。批次执行器/前台服务在每次任务结束时调用 [record]。
 */
@Singleton
class HistoryRecorder @Inject constructor(
    private val dao: TaskRunDao,
) {
    suspend fun record(
        taskId: String,
        taskName: String,
        result: TaskResult,
        startTimeMs: Long,
        endTimeMs: Long,
        batchId: String? = null,
    ): Long {
        val entity = TaskRunEntity(
            taskId = taskId,
            taskName = taskName,
            result = result::class.simpleName ?: "Unknown",
            failedReason = (result as? TaskResult.Failed)?.reason,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationMs = endTimeMs - startTimeMs,
            batchId = batchId,
        )
        return dao.insert(entity)
    }

    fun newBatchId(): String = UUID.randomUUID().toString()
}

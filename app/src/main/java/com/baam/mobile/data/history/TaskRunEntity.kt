package com.baam.mobile.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单次任务运行记录。批次执行时每个任务落一条。
 */
@Entity(tableName = "task_runs")
data class TaskRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val taskName: String,
    /** 结果：Success/Failed/Stopped/Timeout */
    val result: String,
    val failedReason: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    /** 批次 id（同一批次的多条记录共享），独立单跑时为 null */
    val batchId: String? = null,
)

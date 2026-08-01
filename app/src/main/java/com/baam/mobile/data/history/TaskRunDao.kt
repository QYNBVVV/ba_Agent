package com.baam.mobile.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRunDao {

    @Insert
    suspend fun insert(run: TaskRunEntity): Long

    /** 最近 N 条运行记录（UI 历史列表用） */
    @Query("SELECT * FROM task_runs ORDER BY startTimeMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<TaskRunEntity>>

    /** 统计某任务最近成功/失败次数 */
    @Query("SELECT COUNT(*) FROM task_runs WHERE taskId = :taskId AND result = :result AND startTimeMs > :sinceMs")
    suspend fun countSince(taskId: String, result: String, sinceMs: Long): Int

    /** 清理 N 天前的记录 */
    @Query("DELETE FROM task_runs WHERE startTimeMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long): Int
}

package com.baam.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务配置存储（DataStore）。
 *
 * v0.2 仅存：
 *  - 启用的任务 id 集合（UI 开关）
 *  - 是否启用恢复重试
 *
 * 后续扩展：每任务的执行次数/时间窗/优先级等，可改为 Room 存结构化配置。
 */

private val Context.taskConfigStore: DataStore<Preferences> by preferencesDataStore(name = "task_config")

@Singleton
class TaskConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val enabledTasksKey = stringSetPreferencesKey("enabled_tasks")
    private val recoveryKey = booleanPreferencesKey("recovery_enabled")

    /** 当前启用的任务 id 集合 */
    val enabledTaskIds: Flow<Set<String>> = context.taskConfigStore.data.map { p ->
        p[enabledTasksKey] ?: emptySet()
    }

    val recoveryEnabled: Flow<Boolean> = context.taskConfigStore.data.map { p ->
        p[recoveryKey] ?: true
    }

    /** 设置某任务启用/禁用 */
    suspend fun setTaskEnabled(taskId: String, enabled: Boolean) {
        context.taskConfigStore.edit { p ->
            val current = p[enabledTasksKey]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.add(taskId) else current.remove(taskId)
            p[enabledTasksKey] = current
        }
    }

    suspend fun setRecoveryEnabled(enabled: Boolean) {
        context.taskConfigStore.edit { p -> p[recoveryKey] = enabled }
    }
}

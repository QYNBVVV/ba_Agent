package com.baam.mobile.engine.task

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务注册表。所有任务通过 Hilt @IntoSet 注入到此，
 * 前台服务/调度器按 id 查找并执行。
 */
@Singleton
class TaskProvider @Inject constructor(
    private val tasks: Set<@JvmSuppressWildcards Task>,
) {
    private val byId: Map<String, Task> = tasks.associateBy { it.id }

    fun all(): List<Task> = byId.values.toList()
    fun get(id: String): Task = byId[id] ?: error("task not found: $id")
    fun has(id: String): Boolean = byId.containsKey(id)
}

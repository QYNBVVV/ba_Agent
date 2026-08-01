package com.baam.mobile.engine.task

import com.baam.mobile.safety.SafetyController
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务运行器：状态机外壳 + 安全护栏。
 *
 * 职责：
 * 1. 包裹每个任务的总时长上限（默认 30 分钟），超时强制结束 —— 防死循环。
 * 2. 注入 [SafetyController]，任务运行期间用户可随时紧急停止。
 * 3. 统一异常捕获，转换为 [TaskResult.Failed]，避免单任务崩溃拖垮服务。
 */
@Singleton
class TaskRunner @Inject constructor(
    private val safety: SafetyController,
    private val defaultTimeoutMs: Long = 30L * 60 * 1000,
) {

    suspend fun run(
        task: Task,
        ctxFactory: () -> TaskContext,
        timeoutMs: Long = defaultTimeoutMs,
    ): TaskResult {
        Timber.i("[TaskRunner] start task=${task.id} timeout=${timeoutMs}ms")
        safety.onTaskStart(task.id)

        return try {
            withTimeout(timeoutMs) {
                val ctx = ctxFactory()
                runCancellable(task, ctx)
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("[TaskRunner] timeout task=${task.id}")
            TaskResult.Timeout
        } catch (e: Exception) {
            Timber.e(e, "[TaskRunner] crash task=${task.id}")
            TaskResult.Failed(e.message ?: "unknown error")
        } finally {
            safety.onTaskEnd(task.id)
        }
    }

    /**
     * 可取消的任务执行：每个关键步骤前检查 [SafetyController.isStopRequested]。
     * 任务实现内部也应主动检查 [TaskContext.isStopRequested]。
     */
    private suspend fun runCancellable(task: Task, ctx: TaskContext): TaskResult {
        return try {
            task.run(ctx)
        } catch (_: TaskStoppedException) {
            TaskResult.Stopped
        }
    }
}

/** 任务执行中被停止时抛出，用于快速跳出深层调用栈 */
class TaskStoppedException : RuntimeException("task stopped by user")

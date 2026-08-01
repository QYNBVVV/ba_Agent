package com.baam.mobile.engine.task

import com.baam.mobile.engine.scene.Navigator
import com.baam.mobile.safety.SafetyController
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务运行器：状态机外壳 + 安全护栏 + 恢复策略。
 *
 * 职责：
 *  1. 总时长上限（默认 30 分钟），超时强制结束 —— 防死循环。
 *  2. 注入 [SafetyController]，用户可随时紧急停止。
 *  3. 异常捕获，转换为 [TaskResult.Failed]，单任务崩溃不拖垮服务。
 *  4. [runWithRecovery]：任务失败时尝试回到主城锚点后重试（最多 N 次），
 *     解决"任务中途遇到未知弹窗/界面，无法继续"的常见问题。
 */
@Singleton
class TaskRunner @Inject constructor(
    private val safety: SafetyController,
    private val defaultTimeoutMs: Long = 30L * 60 * 1000,
) {

    private val navigator = Navigator()

    /**
     * 基础执行：单次跑任务，带超时与异常兜底，不做重试。
     */
    suspend fun run(
        task: Task,
        ctxFactory: () -> TaskContext,
        timeoutMs: Long = defaultTimeoutMs,
    ): TaskResult {
        Timber.i("[TaskRunner] start task=${task.id} timeout=${timeoutMs}ms")
        safety.onTaskStart(task.id)

        return try {
            withTimeout(timeoutMs) {
                task.run(ctxFactory())
            }
        } catch (_: TimeoutCancellationException) {
            Timber.w("[TaskRunner] timeout task=${task.id}")
            TaskResult.Timeout
        } catch (_: TaskStoppedException) {
            Timber.w("[TaskRunner] stopped task=${task.id}")
            TaskResult.Stopped
        } catch (e: Exception) {
            Timber.e(e, "[TaskRunner] crash task=${task.id}")
            TaskResult.Failed(e.message ?: "unknown error")
        } finally {
            safety.onTaskEnd(task.id)
        }
    }

    /**
     * 带恢复策略的执行：失败时回主城后重试，最多 [maxRetries] 次。
     *
     * 适用场景：日常任务对成功率要求高，偶发弹窗/网络抖动导致失败时
     * 自动回到已知锚点（主城）重新走一遍流程。
     *
     * 注意：用户停止（[TaskResult.Stopped]）和总超时不会重试。
     */
    suspend fun runWithRecovery(
        task: Task,
        ctxFactory: () -> TaskContext,
        maxRetries: Int = 2,
        timeoutMs: Long = defaultTimeoutMs,
    ): TaskResult {
        var lastResult: TaskResult = TaskResult.Failed("not started")
        repeat(maxRetries + 1) { attempt ->
            if (safety.isStopRequested) return TaskResult.Stopped
            if (attempt > 0) {
                Timber.i("[TaskRunner] retry task=${task.id} attempt=$attempt")
                // 恢复：尝试回主城，给下次执行一个干净的起点
                val ctx = ctxFactory()
                navigator.goHome(ctx)
            }
            lastResult = run(task, ctxFactory, timeoutMs)
            when (lastResult) {
                TaskResult.Success,
                TaskResult.Stopped,
                TaskResult.Timeout -> return lastResult
                is TaskResult.Failed -> {
                    Timber.w("[TaskRunner] failed, will retry: ${lastResult.reason}")
                }
            }
        }
        return lastResult
    }
}

/** 任务执行中被停止时抛出，用于快速跳出深层调用栈 */
class TaskStoppedException : RuntimeException("task stopped by user")

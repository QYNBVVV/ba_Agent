package com.baam.mobile.safety

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 逃生通道核心控制器（单例）。
 *
 * 五道防线（对应架构方案中的 SafetyController）：
 *  1. 常驻通知停止键         → [requestStop]，由前台服务通知 Action 触发
 *  2. 悬浮窗紧急停止按钮      → [requestStop]，由 [FloatingStopButton] 触发
 *  3. 用户触摸自动暂停        → [onUserTouched]，由 AccessibilityService 监听触摸事件触发
 *  4. 音量下键中断           → [onVolumeKeyDown]，由 AccessibilityService onKeyEvent 触发
 *  5. 总时长上限             → TaskRunner.withTimeout（不在此处）
 *
 * 状态语义：
 *  - [stopRequested]：用户主动请求停止 → 任务立即退出，返回 [TaskResult.Stopped]
 *  - [isPaused]：用户触碰屏幕 → 暂停 [TOUCH_PAUSE_MS]，期间任务空转等待，
 *    超过 [PAUSE_MAX_MS] 仍未恢复则升级为停止（避免卡死）
 *
 * 任务在每个关键步骤前调用 [yieldIfStopped] / [awaitIfPaused]。
 */
@Singleton
class SafetyController @Inject constructor() {

    private val _stopRequested = MutableStateFlow(false)
    val stopRequested: StateFlow<Boolean> = _stopRequested.asStateFlow()

    private val pauseUntil = AtomicLong(0L)
    private val pauseStartedAt = AtomicLong(0L)

    /** 是否处于「用户触碰导致的暂停」期 */
    val isPaused: Boolean
        get() = SystemClock.elapsedRealtime() < pauseUntil.get()

    /** 综合判断：是否应中断任务（停止 或 暂停超时升级） */
    val isStopRequested: Boolean
        get() {
            if (_stopRequested.value) return true
            // 暂停超过上限，升级为停止
            if (isPaused && SystemClock.elapsedRealtime() - pauseStartedAt.get() > PAUSE_MAX_MS) {
                _stopRequested.value = true
                return true
            }
            return false
        }

    /** 1/2/4 防线统一入口：请求立即停止 */
    fun requestStop() {
        _stopRequested.value = true
    }

    /** 3 防线：用户触摸屏幕 → 触发暂停窗口 */
    fun onUserTouched() {
        if (pauseUntil.get() == 0L) {
            pauseStartedAt.set(SystemClock.elapsedRealtime())
        }
        pauseUntil.set(SystemClock.elapsedRealtime() + TOUCH_PAUSE_MS)
    }

    /** 4 防线：音量下键 → 立即停止 */
    fun onVolumeKeyDown() {
        requestStop()
    }

    /**
     * 任务在每个关键步骤前调用。若已请求停止则抛 [TaskStoppedException] 快速跳出调用栈。
     */
    fun checkStopOrThrow() {
        if (isStopRequested) throw com.baam.mobile.engine.task.TaskStoppedException()
    }

    /**
     * 暂停等待：若处于暂停期则轮询等待恢复，超时则抛停止异常。
     * 在 engine 的每次截图/点击前调用。
     */
    suspend fun awaitIfPaused() {
        var waited = 0L
        while (isPaused && waited < PAUSE_MAX_MS) {
            checkStopOrThrow()
            delay(150)
            waited += 150
        }
        checkStopOrThrow()
    }

    /** 任务开始：重置状态 */
    fun onTaskStart(taskId: String) {
        _stopRequested.value = false
        pauseUntil.set(0L)
        pauseStartedAt.set(0L)
    }

    /** 任务结束：清理 */
    fun onTaskEnd(taskId: String) {
        pauseUntil.set(0L)
        pauseStartedAt.set(0L)
    }

    companion object {
        /** 触摸后暂停时长 */
        const val TOUCH_PAUSE_MS = 5_000L
        /** 暂停最长容忍时长，超过则升级为停止 */
        const val PAUSE_MAX_MS = 60_000L
    }
}

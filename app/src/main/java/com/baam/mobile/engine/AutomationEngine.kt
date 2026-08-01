package com.baam.mobile.engine

import android.graphics.Bitmap
import com.baam.mobile.data.LogBus
import com.baam.mobile.engine.cv.TemplateMatcher
import com.baam.mobile.engine.driver.DeviceDriver
import com.baam.mobile.engine.input.Input
import com.baam.mobile.engine.task.MatchResult
import com.baam.mobile.engine.task.Scene
import com.baam.mobile.engine.task.TaskContext
import com.baam.mobile.safety.SafetyController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动化引擎：[TaskContext] 的唯一实现，串联 Driver / Matcher / Input / Safety。
 *
 * 所有公开方法在执行前都会：
 *  1. [SafetyController.awaitIfPaused] —— 触摸暂停期间空转等待
 *  2. [SafetyController.checkStopOrThrow] —— 收到停止请求则抛 [TaskStoppedException] 快速跳出
 *
 * 这样业务任务实现里无需手动检查停止，写起来像普通顺序代码。
 */
@Singleton
class AutomationEngine @Inject constructor(
    private val driver: DeviceDriver,
    private val matcher: TemplateMatcher,
    private val safety: SafetyController,
    private val logBus: LogBus,
) : TaskContext {

    private val input = Input(driver)

    override val isStopRequested: Boolean
        get() = safety.isStopRequested

    override suspend fun screenshot(): Bitmap {
        safety.awaitIfPaused()
        safety.checkStopOrThrow()
        return driver.screenshot()
    }

    override suspend fun find(scene: Scene): MatchResult {
        val shot = screenshot()
        return matcher.match(shot, scene.templates, scene.threshold, scene.region)
    }

    override suspend fun waitFor(
        scene: Scene,
        timeoutMs: Long,
        intervalMs: Long,
    ): MatchResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            safety.checkStopOrThrow()
            val r = find(scene)
            if (r.found) return r
            delay(intervalMs)
        }
        return MatchResult.NOT_FOUND
    }

    override suspend fun tap(x: Int, y: Int) {
        safety.awaitIfPaused()
        safety.checkStopOrThrow()
        input.tap(x, y)
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        safety.awaitIfPaused()
        safety.checkStopOrThrow()
        input.swipe(x1, y1, x2, y2, durationMs)
    }

    override suspend fun tapIfFound(scene: Scene): Boolean {
        val r = find(scene)
        if (r.found) {
            tap(r.x, r.y)
            return true
        }
        return false
    }

    override suspend fun waitForLoading(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        // 先等加载圈出现或直接进入加载（最多 2s 检测窗口）
        var detectWindow = 2000L
        while (System.currentTimeMillis() < deadline) {
            safety.checkStopOrThrow()
            // 仍在加载
            if (find(com.baam.mobile.engine.scene.SceneLibrary.LOADING).found) {
                delay(500)
                detectWindow = 0  // 已检测到加载，后续持续等消失
                continue
            }
            // 加载已消失（或从未出现）
            if (detectWindow <= 0) return  // 之前在加载，现在消失了 → 完成
            detectWindow -= 500
            delay(500)
        }
    }

    override suspend fun sleep(ms: Long) {
        val step = 200L
        var remaining = ms
        while (remaining > 0) {
            safety.checkStopOrThrow()
            delay(minOf(step, remaining))
            remaining -= step
        }
    }

    override fun log(msg: String) {
        Timber.i("[Engine] $msg")
        logBus.emit("Engine", msg)
    }
}

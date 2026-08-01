package com.baam.mobile.engine.input

import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityService
import android.view.accessibility.GestureDescription
import com.baam.mobile.engine.driver.DeviceDriver
import com.baam.mobile.engine.screen.CoordinateMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 输入抽象：所有点击/滑动的坐标都基于参考坐标系（1280x720）。
 * 内部通过 [CoordinateMapper] 转成实际像素，再交给 [DeviceDriver] 执行。
 *
 * 点击节流：每次手势之间留 [GAP_MS] 空隙，给系统和用户留反应窗口
 * （也是 SafetyController「触摸自动暂停」生效的前提）。
 */
class Input(
    private val driver: DeviceDriver,
) {
    suspend fun tap(x: Int, y: Int) {
        driver.tap(x, y)
        delay(GAP_MS)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300) {
        driver.swipe(x1, y1, x2, y2, durationMs)
        delay(GAP_MS)
    }

    companion object {
        const val GAP_MS = 80L
    }
}

package com.baam.mobile.engine.driver.impl

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityService
import android.view.accessibility.GestureDescription
import com.baam.mobile.engine.driver.AccessibilityServiceHolder
import com.baam.mobile.engine.driver.DeviceDriver
import com.baam.mobile.engine.screen.CoordinateMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * 未 Root 路径的 DeviceDriver 实现：
 * - 截图：AccessibilityService.takeScreenshot()（API 30+，无需每次授权）
 * - 点击/滑动：AccessibilityService.dispatchGesture()
 *
 * 截图会通过 [CoordinateMapper] 归一化到 1280x720；
 * 输入坐标（参考系）会反向映射到实际像素。
 *
 * 注：低版本（< API 30）无 takeScreenshot，此处抛出明确异常，由调用方回退 MediaProjection（v2）。
 */
class AccessibilityDeviceDriver(
    private val mapper: CoordinateMapper,
) : DeviceDriver {

    override val actualWidth: Int get() = mapper.actualWidth
    override val actualHeight: Int get() = mapper.actualHeight

    override val isReady: Boolean
        get() = AccessibilityServiceHolder.isAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override suspend fun screenshot(): Bitmap = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "takeScreenshot 需要 Android 11+，低版本请回退 MediaProjection"
        }
        val service = AccessibilityServiceHolder.get()

        val result = withTimeoutOrNull(SHOT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    AccessibilityService.TAKE_SCREENSHOT_HARDWARE,
                    ApplicationExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: android.graphics.Bitmap) {
                            // 系统返回的是实际分辨率，归一化到参考系
                            val normalized = mapper.normalizeScreenshot(screenshot)
                            // 仅当归一化产生了新 Bitmap 时才回收原图，避免误回收返回值
                            if (normalized !== screenshot) screenshot.recycle()
                            cont.resume(normalized)
                        }

                        override fun onFailure(errorCode: Int) {
                            Timber.w("takeScreenshot failed code=$errorCode")
                            cont.resume(null)
                        }
                    },
                )
            }
        } ?: error("截图超时")

        result
    }

    override suspend fun tap(x: Int, y: Int) {
        val (ax, ay) = mapper.refToActual(x, y)
        dispatchTap(ax.toFloat(), ay.toFloat())
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        val (ax1, ay1) = mapper.refToActual(x1, y1)
        val (ax2, ay2) = mapper.refToActual(x2, y2)
        dispatchSwipe(ax1.toFloat(), ay1.toFloat(), ax2.toFloat(), ay2.toFloat(), durationMs)
    }

    private suspend fun dispatchTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture)
    }

    private suspend fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture)
    }

    private suspend fun dispatchGesture(gesture: GestureDescription) {
        val service = AccessibilityServiceHolder.get()
        suspendCancellableCoroutine<Unit> { cont ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(Unit) }
                override fun onCancelled(g: GestureDescription?) {
                    Timber.w("gesture cancelled")
                    if (cont.isActive) cont.resume(Unit)
                }
            }, null)
        }
    }

    companion object {
        private const val SHOT_TIMEOUT_MS = 3000L
        private const val TAP_DURATION_MS = 50L
    }
}

/** takeScreenshot 回调要求主线程 Executor */
private val ApplicationExecutor = java.util.concurrent.Executor { command ->
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        command.run()
    } else {
        android.os.Handler(android.os.Looper.getMainLooper()).post(command)
    }
}

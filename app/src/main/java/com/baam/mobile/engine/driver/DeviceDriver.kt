package com.baam.mobile.engine.driver

import android.graphics.Bitmap

/**
 * 设备驱动抽象：所有与设备交互的能力（截图、输入）都藏在接口后。
 * - 未 Root 实现：[com.baam.mobile.engine.driver.impl.AccessibilityDeviceDriver]
 *   使用 AccessibilityService.takeScreenshot + dispatchGesture
 * - Root 实现（预留）：使用 screencap + input tap/swipe
 *
 * 所有坐标均为「参考坐标系（默认 1280x720）」，由 [com.baam.mobile.engine.screen.CoordinateMapper]
 * 负责与实际屏幕分辨率互转，上层任务逻辑无感。
 */
interface DeviceDriver {

    /** 实际屏幕分辨率（像素） */
    val actualWidth: Int
    val actualHeight: Int

    /**
     * 截取当前屏幕。
     * @return 已缩放到参考坐标系的 Bitmap（默认 1280x720），便于直接喂给模板匹配。
     */
    suspend fun screenshot(): Bitmap

    /** 在参考坐标系下点击 (x, y) */
    suspend fun tap(x: Int, y: Int)

    /**
     * 在参考坐标系下从 (x1,y1) 滑动到 (x2,y2)。
     * @param durationMs 滑动时长
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300)

    /** 是否已就绪（无障碍服务已连接 / Root 已授权） */
    val isReady: Boolean
}

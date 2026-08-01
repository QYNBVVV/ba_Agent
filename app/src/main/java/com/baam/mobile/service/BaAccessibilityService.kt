package com.baam.mobile.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.baam.mobile.engine.driver.AccessibilityServiceHolder
import com.baam.mobile.safety.SafetyController
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * 无障碍主服务。承担三件事：
 *  1. attach 自身到 [AccessibilityServiceHolder]，供 Driver 使用
 *  2. 监听用户触摸事件 → 触发 [SafetyController.onUserTouched]（第 3 道防线：触摸自动暂停）
 *  3. 拦截音量下键 → 触发 [SafetyController.onVolumeKeyDown]（第 4 道防线）
 *
 * 注意：本服务配置（accessibility_service_config.xml）刻意不读取任何应用内容，
 * 仅监听 touch/key 事件，最小权限原则。
 */
@AndroidEntryPoint
class BaAccessibilityService : AccessibilityService() {

    @Inject lateinit var safety: SafetyController

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.attach(this)
        Timber.i("BaAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        // 用户触摸/点击屏幕 → 触发暂停
        if (type == AccessibilityEvent.TYPE_VIEW_TOUCHED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            safety.onUserTouched()
        }
    }

    override fun onInterrupt() {
        Timber.w("BaAccessibilityService interrupted")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // 音量下键 → 立即停止（即使屏幕被点满也能物理触发）
        if (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            event.action == KeyEvent.ACTION_UP
        ) {
            safety.onVolumeKeyDown()
            return true // 消费事件，避免同时调音量
        }
        return false
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityServiceHolder.detach()
        Timber.i("BaAccessibilityService unbind")
        return super.onUnbind(intent)
    }
}

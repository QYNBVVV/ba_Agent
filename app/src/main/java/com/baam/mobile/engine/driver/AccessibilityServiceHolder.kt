package com.baam.mobile.engine.driver

import android.view.accessibility.AccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 无障碍服务引用持有器。
 *
 * AccessibilityService 由系统管理生命周期，不能直接由 Hilt 注入。
 * 服务 [com.baam.mobile.service.BaAccessibilityService] 在 onServiceConnected 时
 * 注册自己，onUnbind 时清除。Driver 通过本持有器拿到当前 service 实例。
 */
object AccessibilityServiceHolder {

    private val _service = MutableStateFlow<AccessibilityService?>(null)
    val service: StateFlow<AccessibilityService?> = _service

    val isAvailable: Boolean get() = _service.value != null

    fun attach(service: AccessibilityService) {
        _service.value = service
    }

    fun detach() {
        _service.value = null
    }

    fun get(): AccessibilityService = _service.value
        ?: error("AccessibilityService not attached")
}

package com.baam.mobile.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运行日志总线（单例）。任务运行中的日志通过 [emit] 发出，
 * UI 层订阅 [flow] 实时显示。
 *
 * 用 SharedFlow（replay=200）保留最近 200 条，便于 UI 重建后回填。
 */
@Singleton
class LogBus @Inject constructor() {
    private val _flow = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 64)
    val flow: SharedFlow<String> = _flow.asSharedFlow()

    fun emit(tag: String, msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _flow.tryEmit("[$ts][$tag] $msg")
    }
}

package com.baam.mobile.engine.task

import android.graphics.Bitmap

/**
 * 任务运行结果。
 */
sealed class TaskResult {
    /** 成功完成 */
    data object Success : TaskResult()

    /** 失败（带原因，可触发恢复策略或上报） */
    data class Failed(val reason: String) : TaskResult()

    /** 被用户紧急停止 / 触摸暂停超时退出 */
    data object Stopped : TaskResult()

    /** 触发总时长上限，强制结束 */
    data object Timeout : TaskResult()
}

/**
 * 任务上下文：任务实现只能通过该接口与引擎交互，
 * 不能直接接触 AccessibilityService / DeviceDriver，便于隔离与测试。
 */
interface TaskContext {

    /** 获取当前截图（已归一化到 1280x720） */
    suspend fun screenshot(): Bitmap

    /** 在当前截图上匹配场景，返回匹配结果（坐标为参考系） */
    suspend fun find(scene: Scene): MatchResult

    /** 等待某场景出现，超时返回 NOT_FOUND */
    suspend fun waitFor(scene: Scene, timeoutMs: Long = 10_000, intervalMs: Long = 500): MatchResult

    /** 参考系坐标点击 */
    suspend fun tap(x: Int, y: Int)

    /** 参考系坐标滑动 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300)

    /** 点击场景匹配中心（命中则点击，返回是否点了） */
    suspend fun tapIfFound(scene: Scene): Boolean

    /** 记录日志（UI 可见） */
    fun log(msg: String)

    /** 是否收到停止请求（由 SafetyController 触发）。任务应在每个步骤前主动检查。 */
    val isStopRequested: Boolean
}

/**
 * 业务任务抽象。从开源 BAAS 移植的每个功能（咖啡厅/PvP/委托…）实现一个。
 */
interface Task {
    val id: String
    val displayName: String

    suspend fun run(ctx: TaskContext): TaskResult
}

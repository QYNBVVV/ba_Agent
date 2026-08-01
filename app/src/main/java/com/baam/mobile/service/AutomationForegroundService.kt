package com.baam.mobile.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.baam.mobile.data.LogBus
import com.baam.mobile.engine.AutomationEngine
import com.baam.mobile.engine.task.TaskProvider
import com.baam.mobile.engine.task.TaskResult
import com.baam.mobile.engine.task.TaskRunner
import com.baam.mobile.safety.FloatingStopButton
import com.baam.mobile.safety.SafetyController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 自动化前台服务。
 *
 * 职责：
 *  - 拉起前台通知（第 1 道防线：停止 Action）+ 悬浮窗（第 2 道防线）
 *  - 在协程中执行 [TaskRunner.run]，注入 [AutomationEngine] 作为 TaskContext
 *  - 任务结束/被停后清理资源并 stopSelf
 *
 * 用 [LifecycleService] 获得 lifecycleScope，便于协程随服务销毁而取消。
 */
@AndroidEntryPoint
class AutomationForegroundService : LifecycleService() {

    @Inject lateinit var taskProvider: TaskProvider
    @Inject lateinit var engine: AutomationEngine
    @Inject lateinit var taskRunner: TaskRunner
    @Inject lateinit var safety: SafetyController
    @Inject lateinit var logBus: LogBus
    @Inject lateinit var notificationHelper: NotificationHelper

    private var floatingButton: FloatingStopButton? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        floatingButton = FloatingStopButton(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                Timber.i("收到停止请求（通知/外部）")
                safety.requestStop()
            }
            ACTION_START_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                if (!taskProvider.has(taskId)) {
                    logBus.emit("Service", "未知任务: $taskId")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundAndRun(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAndRun(taskId: String) {
        val task = taskProvider.get(taskId)
        notificationHelper.ensureChannel()
        val notification = notificationHelper.buildRunningNotification(task.displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        // 第 2 道防线：悬浮窗停止按钮
        floatingButton?.show { safety.requestStop() }
        logBus.emit("Service", "开始任务: ${task.displayName}")

        lifecycleScope.launch {
            val result = taskRunner.run(task) { engine }
            logBus.emit("Service", "任务结束: $result")

            floatingButton?.dismiss()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        floatingButton?.dismiss()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_TASK = "com.baam.mobile.action.START_TASK"
        const val ACTION_STOP = "com.baam.mobile.action.STOP"
        const val EXTRA_TASK_ID = "task_id"

        fun startTask(context: android.content.Context, taskId: String) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_START_TASK
                putExtra(EXTRA_TASK_ID, taskId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}

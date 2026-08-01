package com.baam.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.baam.mobile.R
import com.baam.mobile.safety.SafetyController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知构造器。任务运行期间常驻通知，承载第 1 道防线（停止按钮 Action）。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /**
     * 构造运行中通知。点击通知本身打开主界面；「立即停止」Action 发停止广播给前台服务。
     */
    fun buildRunningNotification(taskName: String): android.app.Notification {
        val stopIntent = Intent(context, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPi = openIntent?.let {
            PendingIntent.getActivity(
                context, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.fg_notification_title))
            .setContentText("$taskName · ${context.getString(R.string.fg_notification_text)}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.fg_action_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "baam_running"
        const val CHANNEL_NAME = "BAAM 运行"
        const val NOTIFICATION_ID = 0xBAA_0001
    }
}

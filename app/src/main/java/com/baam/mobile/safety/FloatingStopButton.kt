package com.baam.mobile.safety

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Button
import com.baam.mobile.R
import timber.log.Timber

/**
 * 悬浮窗紧急停止按钮（第 2 道防线）。
 *
 * 任务运行期间在屏幕角落显示一个半透明红色「停」按钮，点击立即停止。
 * 即使游戏全屏沉浸、看不到通知栏，从角落点一下也能停。
 *
 * 需要权限：android.permission.SYSTEM_ALERT_WINDOW（Android 6+ 需用户授权「显示在其他应用上层」）。
 * 若权限未授予，则静默跳过（不阻断主流程，由其他防线兜底）。
 */
class FloatingStopButton(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var button: Button? = null

    fun show(onStop: () -> Unit) {
        if (button != null) return
        if (!canDrawOverlays()) {
            Timber.w("SYSTEM_ALERT_WINDOW 未授权，悬浮停止按钮不显示，依赖通知栏/触摸/音量键兜底")
            return
        }

        val btn = Button(context).apply {
            text = "停"
            setBackgroundColor(context.getColor(R.color.baam_danger))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            alpha = 0.85f
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    onStop()
                    true
                } else false
            }
        }

        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        try {
            windowManager.addView(btn, params)
            button = btn
        } catch (e: Exception) {
            Timber.e(e, "添加悬浮停止按钮失败")
        }
    }

    fun dismiss() {
        button?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            button = null
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)
}

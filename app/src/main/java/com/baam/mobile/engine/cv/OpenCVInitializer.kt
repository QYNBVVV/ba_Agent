package com.baam.mobile.engine.cv

import org.opencv.android.OpenCVLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OpenCV native 库初始化。
 * 4.x 推荐用 initLocal()，随 App 进程内加载 .so，无需安装外部 OpenCV Manager。
 */
object OpenCVInitializer {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(): Boolean {
        if (initialized.get()) return true
        val ok = try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            // OpenCV SDK 未集成时给出明确错误，避免误判
            throw IllegalStateException(
                "OpenCV 初始化失败。请确认已集成 OpenCV Android SDK（见 README）。原始错误: ${e.message}", e
            )
        }
        if (ok) initialized.set(true) else throw IllegalStateException("OpenCVLoader.initLocal() 返回 false")
        return true
    }
}

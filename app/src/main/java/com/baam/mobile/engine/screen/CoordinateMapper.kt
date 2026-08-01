package com.baam.mobile.engine.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * 分辨率归一化器。
 *
 * 核心思想：上层所有任务里写死的坐标和模板都基于「参考坐标系 1280x720」
 * （与开源 BAAS 一致，便于直接复用其 PNG 模板）。
 * 运行时按 实际/参考 比例对坐标和截图做缩放，上层任务逻辑完全无感。
 *
 * 注意：横竖屏与 letterbox 黑边场景由 [detectScale] 单独处理（v1 假定游戏全屏无黑边，
 * 黑边裁剪留待后续增强）。
 */
class CoordinateMapper(
    val refWidth: Int = REF_WIDTH,
    val refHeight: Int = REF_HEIGHT,
    val actualWidth: Int,
    val actualHeight: Int,
) {

    /** 参考系坐标 -> 实际屏幕像素 */
    val scaleToActualX: Float get() = actualWidth.toFloat() / refWidth
    val scaleToActualY: Float get() = actualHeight.toFloat() / refHeight

    /** 实际屏幕像素 -> 参考系坐标 */
    val scaleToRefX: Float get() = refWidth.toFloat() / actualWidth
    val scaleToRefY: Float get() = refHeight.toFloat() / actualHeight

    /** 参考系 (x,y) -> 实际像素 */
    fun refToActual(x: Int, y: Int): Pair<Int, Int> =
        (x * scaleToActualX).roundToInt() to (y * scaleToActualY).roundToInt()

    /** 实际像素 (x,y) -> 参考系 */
    fun actualToRef(x: Int, y: Int): Pair<Int, Int> =
        (x * scaleToRefX).roundToInt() to (y * scaleToRefY).roundToInt()

    /**
     * 将实际截图缩放到参考系（1280x720），返回新 Bitmap。
     * 这样模板匹配时，截图与模板分辨率一致，可直接 matchTemplate。
     */
    fun normalizeScreenshot(src: Bitmap): Bitmap {
        if (src.width == refWidth && src.height == refHeight) return src
        val out = Bitmap.createBitmap(refWidth, refHeight, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            setScale(refWidth.toFloat() / src.width, refHeight.toFloat() / src.height)
        }
        val scaled = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        Canvas(out).drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (scaled !== src) scaled.recycle()
        return out
    }

    companion object {
        const val REF_WIDTH = 1280
        const val REF_HEIGHT = 720
    }
}

package com.baam.mobile.engine.task

import android.graphics.Rect

/**
 * 场景匹配结果。坐标均为参考坐标系（1280x720）。
 */
data class MatchResult(
    val found: Boolean,
    val x: Int = 0,
    val y: Int = 0,
    val confidence: Float = 0f,
) {
    companion object {
        val NOT_FOUND = MatchResult(found = false)
    }
}

/**
 * 场景定义：一组模板图片 + 匹配阈值 + 可选限定区域。
 *
 * 用法：用当前截图去匹配某个 Scene，命中即代表游戏处于该场景，
 * 进而决定下一步动作（点击某按钮 / 等待 / 回退）。
 *
 * @param templates assets 下的 PNG 路径列表（任一命中即视为该场景）
 * @param threshold 匹配置信度阈值，默认 0.85
 * @param region 仅在此区域（参考系）内匹配，提速且减少误判；null 为全屏
 */
data class Scene(
    val id: String,
    val templates: List<String>,
    val threshold: Float = 0.85f,
    val region: Rect? = null,
)

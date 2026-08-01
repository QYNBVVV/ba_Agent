package com.baam.mobile.engine.scene

import com.baam.mobile.engine.task.MatchResult
import com.baam.mobile.engine.task.TaskContext
import kotlinx.coroutines.delay

/**
 * 弹窗清道夫：在任务关键步骤间调用，自动关闭阻塞流程的弹窗。
 *
 * 游戏中常见的中断源：
 *  - 每日公告弹窗
 *  - 网络错误重试框
 *  - 奖励领取确认框
 *  - 各种「确认/关闭」按钮
 *
 * 设计：单次 [sweepOnce] 扫一遍所有已知弹窗，命中则处理一个并返回 true
 * （处理完一个通常界面会变，需要重新截图，故不连击）。
 * [sweep] 循环调用 [sweepOnce] 直到无弹窗或达到最大轮数。
 *
 * 与 Navigator 配合：导航每一步前先 [sweep]，保证不被弹窗卡住。
 */
class PopupHandler {

    /**
     * 扫一次弹窗，命中并处理返回 true；无弹窗返回 false。
     * @param ctx 任务上下文
     */
    suspend fun sweepOnce(ctx: TaskContext): Boolean {
        // 公告 → 点关闭
        if (ctx.tapIfFound(SceneLibrary.POPUP_CLOSE)) {
            ctx.log("关闭公告/弹窗")
            delay(400)
            return true
        }
        // 奖励确认 / 通用 OK → 点确认
        if (ctx.tapIfFound(SceneLibrary.POPUP_REWARD_CONFIRM)) {
            ctx.log("确认奖励弹窗")
            delay(400)
            return true
        }
        if (ctx.tapIfFound(SceneLibrary.COMMON_OK)) {
            ctx.log("点击 OK/确认")
            delay(400)
            return true
        }
        // 网络错误 → 点重试
        if (ctx.tapIfFound(SceneLibrary.NETWORK_ERROR)) {
            ctx.log("网络错误，点击重试")
            delay(2000)  // 等待网络恢复
            return true
        }
        return false
    }

    /**
     * 反复扫弹窗直到清空或达到 [maxRounds]。
     * @return 是否处理过至少一个弹窗
     */
    suspend fun sweep(ctx: TaskContext, maxRounds: Int = 5): Boolean {
        var handled = false
        repeat(maxRounds) {
            if (!sweepOnce(ctx)) return@repeat
            handled = true
        }
        return handled
    }
}

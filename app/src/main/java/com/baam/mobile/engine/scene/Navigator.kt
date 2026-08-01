package com.baam.mobile.engine.scene

import com.baam.mobile.engine.task.MatchResult
import com.baam.mobile.engine.task.TaskContext
import kotlinx.coroutines.delay

/**
 * 导航器：解决"任务中途出错/游戏被切到未知界面，如何回到已知锚点"的问题。
 *
 * 核心策略（与开源 BAAS 思路一致）：
 *  1. [goHome]：从任意界面回到主城（HOME 锚点）。最多重试 N 次，
 *     每轮：先清弹窗 → 若已在 HOME 则成功 → 否则按返回键/点关闭，逐步回退。
 *  2. [ensureScene]：确保当前处于目标场景，未在则先 goHome 再尝试进入。
 *
 * 这是 TaskRunner 恢复策略的基础：任务异常时回退到 HOME，再重新走流程。
 *
 * 注：Android 返回键无障碍无法直接发，但可通过点击右上角"返回"按钮或
 * 系统手势（左滑边缘）实现。此处用 [TaskContext.swipe] 模拟返回手势，
 * 配合 [PopupHandler] 关闭弹窗。
 */
class Navigator(
    private val popupHandler: PopupHandler = PopupHandler(),
) {

    /**
     * 回到主城。最多尝试 [maxAttempts] 轮。
     * @return 是否成功到达 HOME
     */
    suspend fun goHome(ctx: TaskContext, maxAttempts: Int = 8): Boolean {
        ctx.log("导航：回主城")
        repeat(maxAttempts) { attempt ->
            if (ctx.isStopRequested) return false

            // 先清弹窗
            popupHandler.sweep(ctx)

            // 已在主城
            if (ctx.find(SceneLibrary.HOME).found) {
                ctx.log("已到达主城")
                return true
            }

            // 加载中 → 等待
            if (ctx.find(SceneLibrary.LOADING).found) {
                ctx.log("加载中，等待...")
                delay(1500)
                return@repeat
            }

            // 尝试点关闭按钮
            if (ctx.tapIfFound(SceneLibrary.POPUP_CLOSE)) {
                delay(500)
                return@repeat
            }

            // 模拟返回手势（从屏幕左边缘向右滑）
            ctx.swipe(20, 360, 200, 360, durationMs = 200)
            delay(800)

            // 若多轮仍在未知界面，可能是深层菜单，多点几次返回
            if (attempt >= 3) {
                ctx.tap(40, 40)  // 左上角常见返回区
                delay(500)
            }
        }
        ctx.log("⚠ 未能回到主城（可能游戏界面异常）")
        return false
    }

    /**
     * 确保处于目标场景。若不在，先回主城，再执行 [enter] 进入。
     * @param enter 从主城进入目标场景的逻辑（如点击某入口按钮）
     * @return 是否成功到达目标场景
     */
    suspend fun ensureScene(
        ctx: TaskContext,
        target: com.baam.mobile.engine.task.Scene,
        enter: suspend () -> Unit,
    ): Boolean {
        // 先清弹窗
        popupHandler.sweep(ctx)

        // 已在目标场景
        if (ctx.find(target).found) {
            ctx.log("已在目标场景 ${target.id}")
            return true
        }

        // 回主城
        if (!goHome(ctx)) return false

        // 执行进入逻辑
        ctx.log("进入目标场景 ${target.id}")
        enter()

        // 等待到达目标场景（最多 15s）
        val r = ctx.waitFor(target, timeoutMs = 15_000, intervalMs = 800)
        if (!r.found) {
            ctx.log("⚠ 未能进入 ${target.id}")
            return false
        }

        // 进入后再清一次弹窗（进入新场景常伴随弹窗）
        popupHandler.sweep(ctx)
        return true
    }
}

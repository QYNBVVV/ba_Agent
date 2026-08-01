package com.baam.mobile.domain.tasks

import com.baam.mobile.engine.scene.Navigator
import com.baam.mobile.engine.scene.PopupHandler
import com.baam.mobile.engine.scene.SceneLibrary
import com.baam.mobile.engine.task.Task
import com.baam.mobile.engine.task.TaskContext
import com.baam.mobile.engine.task.TaskResult
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 咖啡厅任务（首个真实业务任务，从开源 BAAS 思路移植）。
 *
 * 流程：
 *  1. 清弹窗 → 回主城
 *  2. 进入咖啡厅（点主城咖啡厅入口）
 *  3. 领取咖啡（点 CAFE_COLLECT，若无则跳过）
 *  4. 摸头（多次点击屏幕中下方区域，覆盖学生位置；最多 N 轮）
 *  5. 返回主城（点返回）
 *
 * 特点：
 *  - 全程使用 [Navigator] 与 [PopupHandler]，体现框架的恢复能力
 *  - 每步都有「等待场景出现」+ 超时检测，避免卡死
 *  - 摸头采用区域多点扫描（学生位置随机），而非固定坐标
 *
 * 模板依赖（需从 BAAS assets 搬运到 app/src/main/assets/templates/cafe/）：
 *  - cafe_main.png     咖啡厅主界面标识
 *  - collect_btn.png   领取按钮
 *  - pat_heart.png     学生头上爱心
 *  - no_reward.png     无奖励提示（可选）
 */
class CafeTask @Inject constructor() : Task {

    override val id: String = "cafe"
    override val displayName: String = "咖啡厅（领奖+摸头）"

    private val navigator = Navigator()
    private val popupHandler = PopupHandler()

    /** 主城咖啡厅入口按钮坐标（参考系 1280x720） */
    private val cafeEntryX = 640
    private val cafeEntryY = 360

    override suspend fun run(ctx: TaskContext): TaskResult {
        ctx.log("=== 咖啡厅任务开始 ===")

        // 1. 清弹窗 + 回主城
        popupHandler.sweep(ctx)
        if (!navigator.goHome(ctx)) {
            ctx.log("⚠ 无法回到主城，任务失败")
            return TaskResult.Failed("无法回到主城")
        }

        // 2. 进入咖啡厅
        ctx.log("点击咖啡厅入口 ($cafeEntryX,$cafeEntryY)")
        ctx.tap(cafeEntryX, cafeEntryY)
        ctx.waitForLoading(15_000)

        // 等待咖啡厅界面出现
        val entered = ctx.waitFor(SceneLibrary.CAFE_MAIN, timeoutMs = 10_000, intervalMs = 800)
        if (!entered.found) {
            ctx.log("⚠ 未能进入咖啡厅")
            return TaskResult.Failed("进入咖啡厅失败")
        }
        ctx.log("已进入咖啡厅")
        popupHandler.sweep(ctx)

        // 3. 领取咖啡
        val collected = collectCoffee(ctx)
        ctx.log(if (collected) "咖啡已领取" else "无咖啡可领或领取失败")

        // 4. 摸头
        val patCount = patStudents(ctx)
        ctx.log("摸头完成，共摸 $patCount 次")

        // 5. 返回主城
        ctx.log("返回主城")
        ctx.tap(40, 40)  // 左上角返回
        ctx.sleep(800)
        navigator.goHome(ctx, maxAttempts = 4)

        ctx.log("=== 咖啡厅任务结束 ===")
        return TaskResult.Success
    }

    /**
     * 领取咖啡：点 CAFE_COLLECT，若点中则等待奖励弹窗确认。
     * @return 是否成功领取
     */
    private suspend fun collectCoffee(ctx: TaskContext): Boolean {
        // 检测无奖励提示，直接返回
        if (ctx.find(SceneLibrary.CAFE_NO_REWARD).found) {
            ctx.log("检测到无奖励提示")
            return false
        }

        // 尝试点领取按钮
        if (!ctx.tapIfFound(SceneLibrary.CAFE_COLLECT)) {
            ctx.log("未找到领取按钮")
            return false
        }
        ctx.log("点击领取按钮")
        ctx.sleep(1000)

        // 处理可能的奖励确认弹窗
        popupHandler.sweep(ctx)
        return true
    }

    /**
     * 摸头：在屏幕中下方区域扫描爱心，点到没爱心为止。
     * 学生位置随机，爱心小，故采用多轮扫描 + 区域兜底点击。
     * @return 摸头次数
     */
    private suspend fun patStudents(ctx: TaskContext, maxRounds: Int = 12): Int {
        var count = 0
        repeat(maxRounds) {
            if (ctx.isStopRequested) return count

            // 清弹窗（摸头可能触发对话）
            if (popupHandler.sweep(ctx)) {
                ctx.sleep(300)
            }

            // 找爱心
            val r = ctx.find(SceneLibrary.CAFE_PAT)
            if (r.found) {
                ctx.tap(r.x, r.y)
                count++
                ctx.sleep(400)
            } else {
                // 连续两轮无爱心 → 摸完了
                ctx.sleep(300)
                if (!ctx.find(SceneLibrary.CAFE_PAT).found) {
                    ctx.log("无更多爱心，结束摸头")
                    return count
                }
            }
        }
        return count
    }
}

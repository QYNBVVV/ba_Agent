package com.baam.mobile.domain.tasks

import com.baam.mobile.engine.task.Scene
import com.baam.mobile.engine.task.Task
import com.baam.mobile.engine.task.TaskContext
import com.baam.mobile.engine.task.TaskResult
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Hello 任务：端到端验证「截图 → 模板匹配 → 点击」最小闭环。
 *
 * 流程：
 *  1. 截图并报告分辨率归一化是否生效（截图应为 1280x720）
 *  2. 尝试匹配示例场景 templates/hello/sample.png（若模板缺失则日志提示，不阻断）
 *  3. 命中则点击匹配中心；未命中则在参考系中心点击一次作为演示
 *  4. 全程日志输出，便于在 UI 上观察引擎是否正常工作
 *
 * 这是一个可被替换的脚手架任务，真实任务（咖啡厅/PvP/委托…）从开源 BAAS 移植，
 * 实现同样的 [Task] 接口即可。
 */
class HelloTask @Inject constructor() : Task {

    override val id: String = "hello"
    override val displayName: String = "Hello 自检任务"

    private val sampleScene = Scene(
        id = "hello_sample",
        templates = listOf("templates/hello/sample.png"),
        threshold = 0.85f,
    )

    override suspend fun run(ctx: TaskContext): TaskResult {
        ctx.log("Hello 任务开始")

        // 1. 截图自检：验证归一化
        val shot = ctx.screenshot()
        ctx.log("截图尺寸=${shot.width}x${shot.height}（参考系应为 1280x720）")
        if (shot.width != 1280 || shot.height != 720) {
            ctx.log("⚠ 截图未归一化，请检查 CoordinateMapper")
        }

        // 2. 模板匹配自检
        ctx.log("开始匹配示例场景 sample.png")
        val r = ctx.find(sampleScene)
        if (r.found) {
            ctx.log("匹配命中 conf=${"%.3f".format(r.confidence)} @ (${r.x},${r.y})")
            ctx.tap(r.x, r.y)
            ctx.log("已点击匹配中心")
        } else {
            ctx.log("示例模板未命中（正常，脚手架无真实模板）→ 点击屏幕中心作演示")
            ctx.tap(640, 360)
        }

        delay(500)
        ctx.log("Hello 任务结束")
        return TaskResult.Success
    }
}

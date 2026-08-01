package com.baam.mobile.engine.scene

import android.graphics.Rect
import com.baam.mobile.engine.task.Scene

/**
 * 通用场景常量库。集中定义全游戏通用的场景模板路径与匹配参数，
 * 避免在各任务里散落硬编码。
 *
 * 路径约定：templates/<分类>/<场景>.png，与开源 BAAS assets 结构对齐，
 * PNG 可直接复用 BAAS 同名文件。
 *
 * ⚠ v0.2 阶段：路径已定义但 PNG 尚未打包，匹配会日志提示「模板未命中」，
 * 不影响代码编译与流程编排。真实 PNG 由用户从 BAAS 搬运到 assets。
 */
object SceneLibrary {

    // ---------- 通用锚点 ----------
    /** 主界面（主城/咖啡厅入口所在场景），所有任务的「回家」锚点 */
    val HOME = Scene(
        id = "home",
        templates = listOf("templates/common/home.png"),
        threshold = 0.85f,
    )

    /** 加载中转圈，出现时应等待而非操作 */
    val LOADING = Scene(
        id = "loading",
        templates = listOf("templates/common/loading.png"),
        threshold = 0.80f,
    )

    /** 网络错误 / 断线弹窗 */
    val NETWORK_ERROR = Scene(
        id = "network_error",
        templates = listOf(
            "templates/common/network_error.png",
            "templates/common/retry_button.png",
        ),
        threshold = 0.80f,
    )

    // ---------- 通用弹窗 ----------
    /** 公告弹窗（每日首次进入常出现） */
    val POPUP_ANNOUNCEMENT = Scene(
        id = "popup_announcement",
        templates = listOf("templates/common/popup_announcement.png"),
        threshold = 0.80f,
    )

    /** 奖励领取弹窗（领完东西后的「确认」按钮） */
    val POPUP_REWARD_CONFIRM = Scene(
        id = "popup_reward_confirm",
        templates = listOf("templates/common/popup_reward_confirm.png"),
        threshold = 0.85f,
        region = Rect(800, 600, 1280, 720),  // 通常在右下角
    )

    /** 通用「关闭」按钮（右上角 ×） */
    val POPUP_CLOSE = Scene(
        id = "popup_close",
        templates = listOf("templates/common/popup_close.png"),
        threshold = 0.85f,
        region = Rect(1100, 0, 1280, 200),
    )

    /** 通用「确认/OK」按钮（屏幕中下方） */
    val COMMON_OK = Scene(
        id = "common_ok",
        templates = listOf("templates/common/btn_ok.png"),
        threshold = 0.85f,
        region = Rect(400, 500, 880, 720),
    )

    // ---------- 咖啡厅场景 ----------
    /** 咖啡厅主界面（已进入咖啡厅） */
    val CAFE_MAIN = Scene(
        id = "cafe_main",
        templates = listOf("templates/cafe/cafe_main.png"),
        threshold = 0.80f,
    )

    /** 咖啡厅「领取」按钮（有咖啡可领时高亮） */
    val CAFE_COLLECT = Scene(
        id = "cafe_collect",
        templates = listOf("templates/cafe/collect_btn.png"),
        threshold = 0.85f,
        region = Rect(0, 600, 640, 720),
    )

    /** 摸头爱心按钮（学生头上出现） */
    val CAFE_PAT = Scene(
        id = "cafe_pat",
        templates = listOf("templates/cafe/pat_heart.png"),
        threshold = 0.70f,  // 爱心小且动效，阈值放宽
    )

    /** 咖啡厅已无奖励提示（无咖啡可领） */
    val CAFE_NO_REWARD = Scene(
        id = "cafe_no_reward",
        templates = listOf("templates/cafe/no_reward.png"),
        threshold = 0.80f,
    )

    // ---------- 所有可能阻塞的弹窗集合 ----------
    /** 遇到这些场景时优先处理（关闭/确认），再继续主流程 */
    val INTERRUPTING_POPUPS = listOf(
        POPUP_ANNOUNCEMENT,
        POPUP_REWARD_CONFIRM,
        NETWORK_ERROR,
    )
}

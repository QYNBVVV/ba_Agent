# BAAM Mobile

蔚蓝档案（Blue Archive）**移动端**自动化框架。以安卓 App 形式直接运行在手机上，通过无障碍服务控制同机游戏，无需电脑常开。架构思路与开源 BAAS 对齐，PNG 模板可直接复用，业务逻辑用 Kotlin 重写。

> 本项目仅做技术框架与个人学习用途。无障碍点击属外挂范畴，使用风险自负。

## 一、架构

```
UI(Compose) → 调度(WorkManager/Alarm) → 引擎(TaskRunner/Scene/CV/Driver)
  → 领域(Tasks) → 数据(DataStore/Room/assets)
```

核心抽象：
- `DeviceDriver`：截图/点击接口。未 Root 用无障碍（`takeScreenshot`+`dispatchGesture`），预留 Root 实现（`screencap`+`input`）。
- `CoordinateMapper`：参考坐标系 1280×720，运行时按实际分辨率缩放，上层任务无感。
- `TemplateMatcher`：OpenCV `TM_CCOEFF_NORMED`，与 BAAS 同算法，模板可直搬。
- `Scene`/`Task`/`TaskRunner`：场景识别 + 任务状态机 + 超时护栏。
- `SafetyController`：逃生通道（见下）。

## 二、逃生通道（防锁死）

无障碍点击是注入手势，不独占输入，**不会真正锁死**，但内置五道防线：

| 防线 | 触发 | 实现 |
|---|---|---|
| 1 常驻通知停止键 | 下拉通知点「立即停止」 | `NotificationHelper` + `AutomationForegroundService` |
| 2 悬浮窗停止按钮 | 点屏幕角落红「停」 | `FloatingStopButton` |
| 3 触摸自动暂停 | 用户一碰屏幕，暂停 5s | `BaAccessibilityService` 监听 → `SafetyController.onUserTouched` |
| 4 音量下键中断 | 物理按键 | `onKeyEvent` → `SafetyController.onVolumeKeyDown` |
| 5 总时长上限 | 单任务最长 30 分钟 | `TaskRunner.withTimeout` |

物理兜底：电源键熄屏即停；长按电源键重启可 100% 退出。

## 三、构建与运行

### 1. 环境要求
- Android Studio Hedgehog+ / AGP 8.5
- JDK 17
- Android 11+ 真机（`takeScreenshot` 需 API 30）

### 2. 集成 OpenCV（必做）
本仓库未内置 OpenCV SDK。两种方式任选：

- **方式 A（推荐，Maven）**：`app/build.gradle.kts` 已含 `implementation("org.opencv:opencv:4.9.0")`。若该坐标在你的仓库源不可用，见方式 B。
- **方式 B（手动 SDK）**：从 [opencv.org](https://opencv.org/releases/) 下载 Android SDK，将 `sdk` 作为子模块引入，或把 `opencv-4.9.0-sdk/sdk/libs/opencv-4.9.0-android-sdk.aar` 放入 `app/libs/` 并改为 `implementation(files("libs/opencv.aar"))`。

### 3. 生成 Gradle Wrapper
仓库未带 `gradle-wrapper.jar`（二进制）。任选其一：
- 用 Android Studio 直接打开本目录，IDE 自动生成。
- 或本机装 Gradle 8.7 后在项目根目录执行 `gradle wrapper`。

### 4. 运行
1. 安装到真机。
2. App 内引导开启：**无障碍服务**（选 BAAM 自动化）、**悬浮窗权限**、**关闭电池优化**。
3. 把游戏切到 1280×720 模式（若模拟器/改机支持）。
4. 在 App 点「启动」跑 Hello 自检任务，观察日志：
   - 截图尺寸应为 1280×720（验证归一化）
   - 模板未命中属正常（脚手架无真实模板），会点击屏幕中心作演示

## 四、移植 BAAS 任务

1. 从 BAAS assets 搬运对应 PNG 到 `app/src/main/assets/templates/<task>/`。
2. 新建 `domain/tasks/XxxTask.kt` 实现 `Task` 接口，用 `Scene` 定义场景，用 `TaskContext` 的 `find/waitFor/tap/tapIfFound` 编排流程。
3. 在 `di/TaskModule` 用 `@IntoSet` 注册一次。
4. 任务自动出现在 UI 任务列表。

## 五、目录结构

```
app/src/main/java/com/baam/mobile/
├─ BAAMApp.kt                      # @HiltAndroidApp
├─ di/                             # Hilt 模块（Engine/Task）
├─ engine/
│  ├─ driver/                      # DeviceDriver 接口 + 无障碍实现 + Holder
│  ├─ screen/CoordinateMapper.kt   # 分辨率归一化
│  ├─ input/Input.kt               # 点击节流
│  ├─ cv/                          # OpenCV 初始化 + 模板匹配
│  ├─ task/                        # Scene/Task/TaskRunner/TaskProvider
│  └─ AutomationEngine.kt          # TaskContext 实现
├─ domain/tasks/                   # 业务任务（从 BAAS 移植）
├─ safety/                         # SafetyController + 悬浮窗停止键
├─ service/                        # 无障碍服务 + 前台服务 + 通知
├─ data/LogBus.kt                  # 日志总线
└─ ui/                             # Compose 界面 + ViewModel
app/src/main/assets/templates/     # BAAS PNG 模板
```

## 六、路线

- ✅ 阶段 0：工程骨架 + DeviceDriver + OpenCV + 逃生通道
- ✅ 阶段 1：分辨率归一化 + 模板匹配 + Hello 端到端闭环
- ⬜ 阶段 2：TaskRunner 恢复策略 + 移植咖啡厅任务（首个真实任务）
- ⬜ 阶段 3：批量移植日常/战斗/活动任务
- ⬜ 阶段 4：WorkManager 调度 + 多账号 + 通知
- ⬜ 阶段 5：Root 驱动 + 模板热更

## 七、已知限制（v0.1）

- 仅未 Root 路径；Root 驱动待阶段 5。
- 未处理游戏 letterbox 黑边（假定全屏无黑边）。
- 单账号；多账号待阶段 4。
- 无 MediaProjection 回退（低版本机型暂不支持）。

BAAM 模板目录
================

本目录存放 OpenCV 模板匹配用的 PNG，结构与开源 BAAS 一致，可直接复用其模板包。

约定：
- 所有模板基于「参考坐标系 1280×720」裁剪。
- 截图运行时会被归一化到 1280×720，因此模板与截图分辨率一致，可直接 matchTemplate。
- 路径示例：
    templates/hello/sample.png        —— Hello 自检任务用
    templates/cafe/enter.png          —— 咖啡厅入口按钮
    templates/common/home.png         —— 主界面（回退锚点）
    templates/pvp/...

模板缺失时 TemplateMatcher 会日志提示并跳过，不会崩溃。
真实模板请从开源 BAAS 项目 assets 目录搬运，或自行从 1280×720 截图裁剪。

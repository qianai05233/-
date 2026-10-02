# APK V3.1（热修：启动闪退根因修复）

- **修复下载后打开即闪退**：蓝焰怪（FLAME）状态机引用的动画 `flame_idle / flame_burst / flame_die`
  从未登记进素材管线 `sheet_defs.json` 的 anims 段 → `frames.json` 清单缺项 →
  启动后第一帧绘制敌人即抛 `IllegalStateException` 崩溃
  （`logcat -b crash` 实证，堆栈 `GameScreen.drawEnemies → FramesManifest.anim`）。
- **数据补齐**：重跑素材管线，动画清单 21 → 24 条；图集布局零变化（102 区域原样）。
- **管线加固**：`sprite_pipeline.py` 现在对 core resources 与 android assets **双写** frames.json，
  杜绝两份清单靠手抄同步的漂移隐患。
- **回归契约测试**：单测遍历 Enemy 状态机全部可达状态的 `animId()` 输出、断言 frames.json 必须存在，
  同类缺项在 CI 即被拦截，不会再流到真机。
- versionCode 5 / versionName 0.2.3-p1-fix。

## 素材说明

美术全部来自 assets/ 既有素材，**无新增图片**：蓝焰怪三帧动画的图集区域早已打包，
本次只是补上清单登记（详见 docs/CHANGELOG.md）。

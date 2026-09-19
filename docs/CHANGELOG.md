# Changelog

本文件遵循 ENGINEERING §8：每个 PR 更新。格式：阶段 → 版本 → 内容。

## [0.2.0-p1] APK V2 — P1 手感 + 真帧 + 音效

### 素材管线（tools/pipeline/）
- `sprite_pipeline.py + sheet_defs.json`：AI 帧表自动测量（投影分割）+ 逐行人工校准 →
  抠底(#0D1220 变体自适配) → 世界比例烘焙缩放(BOX) → 主连通域 pivot → shelf 装箱 →
  中位切分量化(palette≤64, PNG8+tRNS) → `android/assets/game/atlas/`（单页 24KB/102 区域）
  → `core/src/main/resources/frames.json`（21 动画，fps/loop/归一化 pivot）+ 预览条。

### core（逻辑）
- 主角战斗状态机：三连击(24/26/36)/连击缓冲/命中后 0.15s 取消窗/蓄力 0.35s→58 伤蓄力斩/受击硬直+无敌/死亡重开。
- F1 敌人 AI：幽魂(追击→前摇 0.25s→扑击 0.18s→收招)、蓝焰怪(高速突进变体)；受击白闪/击退/死亡消散。
- Combat 命中结算：暴击 15%×1.75（seeded）、hitstop 60/90ms、屏震分级、飘字事件。
- VFX：CameraFx(trauma+hitstop)、DamageNumbers 池、Effects 池、翻滚残影。
- 音频：AudioBus 接口 + SfxId×18 + BGM 5 层淡入淡出（GdxAudioBus 实现，SFX 节流池）。
- 新增单测：FramesManifest(4)/AudioManifest(3)/Combat(6)/PlayerCombat(9)/CameraFx+飘字+特效(6)/EnemyAi(5)；
  P0 既有测试全绿保持（PerfHarness ≥10x 冗余）。

### android
- assets 新增 game/atlas（图集）+ game/audio（23 OGG ≈0.4MB，全部自建合成）。
- versionCode 2 / versionName 0.2.0-p1。

### CI
- 追加 Release 自动出包：push main 成功 → `gh release create apk-V2`（标题 APK V2），
  附 debug APK + build_report.txt（包体/测试数/60Hz 冗余/run 链接）。原步骤未改动。

## [0.1.0-p0] APK V1 — P0 骨架（PR #2）

- Gradle 8.10.2 wrapper + core/android 双模块；LibGDX 1.13.1 + Kotlin 2.0.21 + KTX 1.13.1-rc1。
- 固定 60Hz 时间步 + 渲染插值；480×270 整数缩放 letterbox；相机跟随+前瞻。
- 左半屏虚拟摇杆 + 右五键；主角占位胶囊跑/跳/滚；土狼 0.08s/跳缓冲 0.12s/翻滚无敌 0.30s/跳缓。
- CI：build-apk.yml（:core:test + :android:assembleDebug + artifact）。

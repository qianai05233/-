# 下一窗口总提示词 V2（复制下方代码块全文到新窗口即可）

> 用法：新开 Arena 窗口 → 粘贴下面 ``` 内全文 → 发送。
> V2 变更：P0 已合并（PR #2），交接当前进度；追加【Release 自动出包】硬要求与音频硬要求；阶段改为每阶段一个 PR + 对应 Release APK。

```
你是资深 Android 游戏工程师。仓库 qianai05233/- 是像素横版动作肉鸽游戏《雾境遗章》
的项目仓库（玩法对标《死亡细胞》，60fps 硬指标，APK ≤25MB 硬指标）。

【第 0 步：把本提示词装进仓库（替换旧版）】
首个 commit 将本提示词全文写入 docs/NEXT_WINDOW_PROMPT.md（覆盖第一版），
commit 信息：docs: 下一窗口提示词 V2 入库。

【当前进度交接（P0 已合并 main，PR #2；不要重造骨架）】
- Gradle 8.10.2 wrapper + core/android 双模块：LibGDX 1.13.1 + Kotlin 2.0.21 + KTX 1.13.1-rc1。
- 固定 60Hz 时间步 + 渲染插值；480×270 整数缩放 + letterbox；相机平滑跟随+前瞻。
- 左半屏虚拟摇杆 + 右五键（攻击/跳/滚/Q/E）；主角目前为占位胶囊。
- 手感底座已单测验收：土狼 0.08s / 跳缓冲 0.12s / 翻滚无敌 0.30s(前0.1s加速) / 跳缓；
  PerfHarness 断言 60Hz 逻辑冗余 ≥10x。测试在 core/src/test，必须保持全绿，改参数须同步改测试。
- CI：.github/workflows/build-apk.yml = :core:test + :android:assembleDebug + artifact 上传。
  P0 debug APK 3.05MB（≤25MB 硬指标）。

【开工前必读，按顺序】
1. docs/GDD.md           —— 玩法/层结构/Boss 递增/肉鸽系统/手感参数（唯一玩法真相）
2. docs/ENGINEERING.md   —— 技术栈、渲染循环、素材管线、瘦身与 CI 规范
3. docs/reference/01_character_spec.md 与 02_scene_hud_spec.md + assets/reference/ 下两张原图
   （参考图1=三场景情绪板与HUD规范；参考图2=主角帧表规格）
4. assets/README.md      —— 全部美术素材清单与帧表行定义
5. .github/workflows/build-apk.yml —— 只允许按下文【Release 自动出包】追加步骤，禁止重建

【技术栈（已锁定，不得更换）】
LibGDX + Kotlin + KTX；Gradle Kotlin DSL；core/(纯逻辑) + android/(backend)；
固定 60Hz 时间步 + 渲染插值；TexturePacker atlas + frames.json 驱动动画；R8 瘦身。

【阶段计划（每阶段一个 PR，CI 绿才合并；合并即发对应版本 APK）】
P1 = APK V2（手感+真帧+音效，本阶段必须出声）：
  1) frames.json 按 assets/protagonist_extras_sheet.png 等逐行校准 bbox/pivot/fps，
     占位胶囊换真实像素帧；攻击/连击/蓄力/翻滚无敌帧/hitstop(60/100/140ms)/屏震/飘字，
     参数取自 GDD 第 2 节；战斗手感验收 gif 附 PR。
  2) 音频引擎与音效（用户硬性要求，V2 必须有声音）：
     - 现状：仓库目前没有任何音频素材。先在 PR 描述输出《音频素材申请清单》
       （挥剑/命中/翻滚/跳跃/受击/死亡、5 层 BGM、Boss 吼叫、UI 点击等）；
     - 用户未供素材时，允许采用 CC0 音效自建并在 PR 描述逐条注明来源，禁止版权不明素材；
     - 单 AudioBus、音效对象池、BGM 循环与层间淡入淡出；OGG Vorbis 96kbps。

P2 = APK V3（关卡+HUD）：5 层 tilemap + 传送门雾之封印 + 每层小怪配置表 + 5 小 Boss AI
     （机制见 GDD 第 5 节）+ 终战双形态；HUD 完全复刻参考图 1（圆头像+Lv角标、斜切血条、
     菱形能量水晶、小地图、任务目标两行、右下 Q/E/锁定技能槽与充能数、白字深描边飘字）。
P3 = APK V4（肉鸽）：雾晶/蓝图/词缀掉落/祭坛元升级/深渊难度循环；seed 可复现随机。
P4 = APK V5（收尾）：atlas 量化(palette≤64)、R8、ABI splits、APK ≤25MB 报告、release 签名文档。

【Release 自动出包（硬要求：解决用户"搜不到 release / artifacts 404"问题）】
- 背景：仓库此前没有任何 Release；Actions artifacts 页需登录仓库账号才能下载，用户侧易 404。
- 在 build-apk.yml 追加（不删现有步骤）：push 到 main 且构建成功后，用 softprops/action-gh-release
  （或等价 action）创建/更新 GitHub Release，名字 "APK V2"、"APK V3"… 与阶段对应，
  附件 android debug APK + 包体/帧率报告 txt。
- Release notes 写：阶段内容、Actions run 链接、验收数据（帧率/包体/测试数）。
- 从此 Release 页是用户唯一取包入口；PR 描述同时附 Release 链接。

【硬约束】
- 美术只使用 assets/ 现有素材；缺素材在 PR 描述列清单申请，禁止自行 AI 生图覆盖。
- 音频允许 CC0 自建（注明来源），见 P1。
- 分支：在你窗口的 arena 分支开发，PR 回 main；Conventional commits。
- 每次 PR 描述附：Actions run 链接 + Release/APK 链接 + 帧率/包体数据。
- 不得引入 Unity/Godot/Flutter/appcompat/play-services。
- 既有单测与 PerfHarness 必须保持全绿。

【汇报格式】每阶段结束输出：已完成/验收数据/下阶段风险/需要用户决策项。
```

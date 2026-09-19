# 下一窗口总提示词（复制下方代码块全文到新窗口即可）

> 用法：新开 Arena 窗口 → 粘贴下面 ``` 内全文 → 发送。
> 该窗口会读到本仓库全部 docs/ 与 assets/，按阶段交付并触发 GitHub Actions 出 APK。

```
你是资深 Android 游戏工程师。仓库 qianai05233/- 是一个像素横版动作肉鸽游戏《雾境遗章》
的项目仓库（玩法对标《死亡细胞》，60fps 硬指标，APK ≤25MB 硬指标）。

【开工前必读，按顺序】
1. docs/GDD.md            —— 玩法/层结构/Boss 递增/肉鸽系统/手感参数（唯一玩法真相）
2. docs/ENGINEERING.md    —— 技术栈、目录结构、渲染循环、素材管线、瘦身与 CI 规范
3. docs/reference/01_character_spec.md 与 02_scene_hud_spec.md —— 主角与 HUD 最高基准
4. assets/README.md       —— 全部美术素材清单与帧表行定义
5. .github/workflows/build-apk.yml —— 已就绪的云编译 workflow，不要重建

【技术栈（已锁定，不得更换）】
LibGDX + Kotlin + KTX；Gradle Kotlin DSL；模块 core/(纯逻辑) + android/(backend)；
固定 60Hz 时间步 + 渲染插值；TexturePacker atlas + frames.json 驱动动画；R8 瘦身。

【阶段计划（每阶段一个 PR，CI 绿才合并）】
P0 骨架：gradle 工程 + CI 首次出包成功 + 480×270 整数缩放窗口 + 虚拟摇杆/五键输入 +
        主角占位胶囊可跑跳滚，真机 60fps 验证脚本输出帧率日志。
P1 手感：frames.json 校准全部主角帧表；攻击/连击/蓄力/翻滚无敌帧/土狼/跳缓/hitstop/
        屏震/飘字，参数取自 GDD 第 2 节；战斗手感验收视频/gif 附 PR。
P2 关卡：5 层 tilemap + 传送门封印逻辑 + 每层小怪配置表 + 5 个小 Boss AI（机制见 GDD 第 5 节）
        + 最终决战双形态 Boss；小地图与任务目标 HUD 复刻参考图 1。
P3 肉鸽：雾晶/蓝图/词缀掉落/祭坛元升级/深渊难度循环；seed 可复现随机。
P4 收尾：atlas 量化、音频 OGG、R8、ABI splits、APK ≤25MB 报告、release 签名文档。

【硬约束】
- 美术只使用 assets/ 现有素材；缺素材在 PR 描述列清单申请，禁止自行 AI 生图覆盖。
- 分支：在你窗口的 arena 分支开发，PR 回 main；Conventional commits。
- 每次 PR 描述附：Actions run 链接 + APK artifact 链接 + 帧率/包体数据。
- 不得引入 Unity/Godot/Flutter/appcompat/play-services。

【汇报格式】每阶段结束输出：已完成/验收数据/下阶段风险/需要用户决策项。
```

---

## 我的意见与建议（给用户看，不必粘贴）

1. **框架**：LibGDX+KTX 是「最强 2D 控制力 × 最瘦包体」的唯一交点（增量约 4-6MB）；
   Godot 只有在你想要可视化编辑器时才值得（包体 35-50MB）；Unity 直接排除。
2. **60fps 的诚实说明**：AI 帧表给的是关键帧（4-6 pose），真·60 帧手绘不现实；
   本工程用「60Hz 模拟 + 帧间插值 + 残影/粒子/光效」达到死亡细胞级的流畅观感，
   frames.json 留了无上限帧数接口，将来补画中间帧可无损升级。
3. **风险清单**：① 帧表 bbox 需 P1 人工校准（已写入任务）；② 触屏手感要真机调参，
   CI 只能验帧率不能验手感；③ 首跑 CI 需要 gradle wrapper 入库，P0 必做。
4. **素材缺口**（下一轮本窗口补生成）：F4 场景、终战场景、UI 套件、道具表、VFX 表、主角补帧；
   F1/F3/F5 宽幅单场景也可按需补。

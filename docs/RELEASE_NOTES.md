# APK V2（P1：手感 + 真帧 + 音效）

- 主角占位胶囊 → **真实像素帧**：frames.json 逐行校准（翻滚 8 帧/胜利 6/死亡 4+消散 3/祭坛 7/觉醒待机 5/觉醒攻击 5），
  AI 帧表抠底 + 按世界比例烘焙缩放 + 脚底 pivot，装箱 atlas（单页 64 色 PNG8，24KB）。
- **战斗手感**（参数取 GDD 第 2 节）：攻击三连击（24/26/36，飘字中值 tier1）、连击缓冲、
  命中后 0.15s 可取消入翻滚/下一击、蓄力 0.35s→2.2 倍蓄力斩、
  翻滚无敌 0.30s（前 0.1s 加速）+ 残影、hitstop 60ms（暴击 90ms）、trauma 屏震分级、
  白字深描边飘字（暴击金色放大）、暴击 ×1.75（seeded rng 可复现）。
- **音频引擎与音效（V2 必须有声音）**：单 AudioBus（SFX 同 ID 30ms 节流 + 每帧 10 语音上限）、
  BGM 5 层循环 + 层间淡入淡出（按楼层切换）；全部音频为本仓库程序化自建（CC0，见 docs/AUDIO_CREDITS.md），
  OGG Vorbis（音乐 ≤96kbps），总占用 ~0.4MB。
- F1 占位敌波次：兜帽幽魂（近战扑击）/ 蓝焰怪（快速突进），受击白闪/死亡消散/碎晶 VFX。
- HUD P1 版：斜切血条 + HP 数值 + FPS + 连击计数 + 横幅提示（P2 按参考图 1 完全复刻）。
- CI 追加 **Release 自动出包**：push main 成功 → 自动创建/更新 Release「APK V2」，
  附 debug APK + 包体/帧率/测试报告（Release 页 = 用户唯一取包入口）。

## 素材说明

美术全部来自 assets/ 既有素材（protagonist_extras_sheet / enemies_f1_wraith_flame / effects_vfx），
无新增图片素材。缺口清单见 PR 描述《素材申请》。

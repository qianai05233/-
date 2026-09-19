# 音频素材来源说明（P1 · 全部自建，零第三方采样）

背景：仓库此前没有任何音频素材。按提示词规则「用户未供素材时，允许采用 CC0 音效自建并逐条注明来源，
禁止版权不明素材」。沙箱网络无法访问 kenney.nl / opengameart.org（出口白名单限制），
故 P1 采用**本仓库程序化合成**（比 CC0 下载更干净：原创 = 仓库自身版权，作者即项目方）。

## 生成方式

- 生成器：`tools/pipeline/gen_audio.py`（numpy 合成 + libsndfile/OGG Vorbis 编码，固定随机种子，可复现重生成）。
- 授权：**CC0 1.0（公有领域）**，由仓库作者以脚本生成，无任何第三方音源/采样/循环。
- 编码：OGG Vorbis；BGM 实测 18–89 kbps（≤96kbps 目标）；音效为短音（容器开销主导，文件 4–11KB）。
- 音频总线：`core/.../runtime/GdxAudio.kt`（单 AudioBus + SFX 节流池 + BGM 5 层淡入淡出）。

## 音效清单（18 枚，逐条来源）

| 文件 | 用途 | 来源（自建合成说明） |
| --- | --- | --- |
| ui_click.ogg | UI 点击 | 自建：1900→1250Hz 正弦扫频 + 指数衰减 |
| ui_confirm.ogg | UI 确认/波次清空 | 自建：660/990Hz 双音上行 |
| jump.ogg | 跳跃 | 自建：240→540Hz 扫频 + 高频噪声触感 |
| roll.ogg | 翻滚 | 自建：700–2600Hz 带通噪声呼啸 + 低频摩擦 |
| slash_1.ogg | 挥剑一段 | 自建：3–6.5kHz 带通噪声扫频 + 4.3kHz 泛音 |
| slash_2.ogg | 挥剑二段 | 自建：同上变调（2.6–6kHz + 3.6kHz） |
| slash_3.ogg | 挥剑三段（重） | 自建：宽带噪声 + 150Hz 体感低频 |
| charge_ready.ogg | 蓄力就绪 | 自建：1174+1760Hz 双正弦 12Hz 颤音 |
| charge_release.ogg | 蓄力释放 | 自建：900Hz→9kHz 噪声扫频 + 双向扫频 |
| hit_1.ogg | 命中一段 | 自建：190Hz 冲击 + 2.6kHz 噪声爆破（tanh 饱和） |
| hit_2.ogg | 命中二段/蓄力命中 | 自建：155Hz 变体 |
| hit_crit.ogg | 暴击 | 自建：120Hz 低频 + 740/1122/1866Hz 金属 ring |
| enemy_hit.ogg | 敌人受击 | 自建：250→150Hz 扫频 + 低通噪声 |
| hurt.ogg | 玩家受击 | 自建：130Hz 谐波堆叠 + 620→210Hz 下扫（drive=2.2 失真） |
| die_hero.ogg | 玩家死亡 | 自建：300→52Hz 长下扫 + 噪声衰减（0.7s） |
| enemy_die.ogg | 敌人死亡 | 自建：520→110Hz 下扫 + 高频碎裂/噼啪 |
| pickup.ogg | 拾取（P3 备用） | 自建：E5–A5–E6 琶音 |
| portal.ogg | 传送门（P2 备用） | 自建：220→880→440Hz 往返扫频 + 5Hz 颤音 + 气声 |

## BGM（5 层，D 小调 110BPM，4 小节 8.73s 无缝循环）

| 文件 | 层 | 内容（自建合成说明） |
| --- | --- | --- |
| fog_0.ogg | L0 氛围底 | Dm–Bb–C–Dm 弦垫（双失谐正弦 + 慢包络） |
| fog_1.ogg | L1 +低音 | 八分音符根音 triangle 波（奇次谐波堆叠） |
| fog_2.ogg | L2 +琶音 | 十六分琶音（方波谐波 + 3/16 拍回声） |
| fog_3.ogg | L3 +打击 | 底鼓(58→38Hz 扫频)、军鼓(2/4 拍)、16 分 Hats |
| fog_4.ogg | L4 全奏 | L3 + 副低频 + 终小节切分底鼓 |

层间切换：`AudioBus.musicLayer(0..4)`（楼层-1），0.8/s 音量渐变淡入淡出（GdxAudioBus.update）。

## 待用户决策

- 若后续提供官方音频素材（音效 WAV/OGG + 5 层 BGM），直接替换 `android/assets/game/audio/` 同名文件即可生效；
  更新本清单来源标注。
- 《音频素材申请清单》（P1 需求目录）：见本阶段 PR 描述。

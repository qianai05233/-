# 素材目录 · 像素风横版肉鸽《雾境遗章》

视觉基准：`docs/reference/01_character_spec.md`（主角）、`docs/reference/02_scene_hud_spec.md`（场景/HUD）。
原参考图补录位：`assets/reference/01_scene_moodboard.png`、`assets/reference/02_character_sheet.png`
（本地拖入后 commit 即可，目录已建好；转录文档已含全部信息）。

## 帧表（行=动作序列，列=帧序；深色底 #0D1220，图内无文字）
| 文件 | 内容 |
| --- | --- |
| `enemies_f1_wraith_flame_sheet.png` | F1：兜帽幽魂(待机/扑击/受击/消散+血条帧)；蓝焰怪(待机/冲刺/爆裂/熄灭) |
| `enemies_f1_forest_extra_sheet.png` | F1 补充：腐木树人苗(4帧)；雾鸦(4帧) |
| `enemies_f2_marsh_sheet.png` | F2：溺亡士兵(4帧)；沼泽水蛭(4帧) |
| `enemies_f3_crystal_sheet.png` | F3：冰傀儡(4帧)；晶刺蛛(4帧) |
| `enemies_f4_catacomb_sheet.png` | F4：骷髅兵(4帧)；烬蝠(4帧) |
| `enemies_f5_castle_sheet.png` | F5：持盾骑士(4帧)；刺客暗影(4帧) |
| `miniboss_f1_treant_elder_sheet.png` | 小Boss1 古树长者 6帧（震地/扫枝/召唤/硬直/死亡） |
| `miniboss_f2_drowned_bishop_sheet.png` | 小Boss2 溺亡主教 6帧（水波/召地手/水遁/死亡） |
| `miniboss_f3_frost_king_sheet.png` | 小Boss3 棱晶霜王 6帧（冰环/冰锥阵/冲撞/碎裂） |
| `miniboss_f4_forged_skeleton_king_sheet.png` | 小Boss4 锻骸骷王 6帧（熔岩锤击/投熔珠/火柱环/灰灭） |
| `miniboss_f5_abyss_knight_sheet.png` | 小Boss5 深渊骑士 6帧（斩击剑气/召唤法阵/硬直/跪亡） |
| `finalboss_deep_sovereign_sheet.png` | 大Boss 深渊之主：P1 王体 5帧 + P2 巨大灵体 4帧 |
| `protagonist_extras_sheet.png` | 主角补帧：翻滚残影/胜利/死亡/祭坛互动/觉醒形态 |

## 场景
| 文件 | 内容 |
| --- | --- |
| `scene_f2_moonfall_marsh.png` | 第2层 月落沼泽·沉没圣所（方形构图，16:9 待补） |
| `scene_f4_catacomb_forge.png` | 第4层 沉眠墓穴·烬火锻炉（16:9 宽幅） |
| `scene_final_arena_throne.png` | 最终决战 深渊王座（16:9 宽幅） |
| （F1/F3/F5 宽幅单场景） | 按需补：目前仅存于参考图 1 三格 |

## UI / 道具 / 特效
| 文件 | 内容 |
| --- | --- |
| `ui_hud_kit.png` | 血条/能量水晶/菱形技能槽(含锁定)/Boss条/小地图框/目标菱形/摇杆与五键/飘字数字样式 |
| `props_items_sheet.png` | 宝箱开合/血瓶/雾晶/金币/蓝图卷轴/五武器拾取座/门/传送门(层间roguelite过渡) |
| `effects_vfx_sheet.png` | 剑气三档/命中星芒/法阵/脚光/残影拖尾/红血粒子/蓝晶粒子/传送漩涡/光柱/闪白/毒滴/烬火星 |

## 风格规范（新增素材必须遵守）
底色 `#0D1220`；靛紫 `#6C5CA8/#8F7FD4`；光效青蓝 `#7FD4FF/#4AA3FF`；敌对红 `#E0405A`；
月光白 `#F2F6FF`。小怪/主角二头身，Boss 四头身；统一冷蓝轮廓光；帧表等距网格、无文字标注。

## 引擎侧约定
所有帧表由 `core/.../frames.json` 描述 bbox/fps/pivot（下一窗口 P1 校准）；
禁止运行时散图加载，统一 TexturePacker atlas（见 docs/ENGINEERING.md §4）。

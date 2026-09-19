# 工程规范 · Android APK（云编译）

## 1. 框架选型结论：**LibGDX + Kotlin + KTX**
| 方案 | 包体增量 | 60fps 掌控力 | 像素适配 | 结论 |
|---|---|---|---|---|
| **LibGDX(Kotlin+KTX)** | ~4-6MB | 完全（自写循环/ECS/输入） | Nearest+整数缩放原生支持 | ✅ 选它：最强 2D 控制力+最瘦 |
| Godot 4 | ~35-50MB | 好 | 好 | 备选：要编辑器才值 |
| Unity | 100MB+ | 好 | 一般 | ❌ 膨胀 |
| Flutter+Flame | ~15-25MB | 中（widget 层开销） | 中 | ❌ 手感上限低 |
| 纯 Kotlin+GLES | ~1-2MB | 完全 | 完全 | ❌ 等于重写引擎，工期爆炸 |

## 2. 仓库结构（下一窗口创建）
```
/ (repo root)
├─ assets/            # 已入库美术素材（本目录只增不改名）
├─ docs/              # GDD/工程/提示词/参考转录
├─ .github/workflows/build-apk.yml   # 已就绪：push android/** 或手动触发
├─ settings.gradle.kts / build.gradle.kts / gradle/
├─ core/              # 纯 Kotlin 游戏逻辑（无 Android 依赖）
│   └─ src/main/kotlin/...  + resources/frames.json, atlas 配置
└─ android/           # LibGDX android backend、AndroidManifest、ic_launcher
```

## 3. 渲染与循环
- 虚拟分辨率 **480×270**，整数倍缩放 + `TextureFilter.Nearest`；viewport letterbox。
- 固定步长 1/60 累加器；渲染插值 alpha；SpriteBatch 单 batch 目标 <3 draw call/层。
- 相机：跟随 + 前瞻 + 屏震trait；hitstop 全局时间缩放实现。

## 4. 素材管线
- AI 帧表 → 人工校准 `core/src/main/resources/frames.json`
  （每 sheet：行=动作、逐帧 bbox、fps、pivot、flip 规则）→ LibGDX TexturePacker 打 atlas
  （`max=2048`、`stripWhitespaceX/Y`、padding 2 防渗色）。
- 图量化：palette ≤64 色/png（pngquant）；音频 OGG Vorbis 96kbps。
- **禁止**运行时加载散 png；全部走 atlas。

## 5. 动画系统（60fps 关键）
关键帧(4-6 pose) + 引擎插值：位移/旋转/缩放 tween + 残影帧缓存 + VFX 层叠加；
`frames.json` 帧数无上限，美术补中间帧即自动生效。

## 6. 瘦身清单（APK ≤25MB 硬指标）
R8 fullMode + resourceShrink；`splits { abi { arm64-v8a, armeabi-v7a } }`；
atlas 总量 ≤6MB、音频 ≤4MB；Kotlin stdlib 经 R8 裁剪；无 androidx 多余依赖；
lint 禁止 play-services / appcompat（仅 game 依赖）。

## 7. CI/CD（GitHub Actions 云编译，不在沙箱编）
- `.github/workflows/build-apk.yml`：push 命中 `android/** core/** *.gradle.kts` 或
  手动 `workflow_dispatch` → ubuntu-latest + JDK17 + gradle → `:android:assembleDebug`
  → artifact `app-debug-apk` 下载即装。
- Release 签名：仓库 Secrets 配 `KEYSTORE_BASE64/PASSWORD/ALIAS`，workflow 已留注释位。

## 8. 分支与协作（下一窗口遵守）
- 本会话分支 `arena/01a0b93b-repo` 为素材/文档源；下一窗口在其自身 arena 分支开发，
  PR 回 `main`；分支命名 `feat/p0-skeleton`、`feat/combat-feel`…；commit 用 Conventional。
- 每个 PR 必须：CI 绿 + 附 APK artifact 链接 + 更新 docs/CHANGELOG.md。

## 9. 性能验收
中端机（骁龙 6 系）60fps ±2；冷启动 ≤3s；内存峰值 ≤250MB；APK ≤25MB。

# 闪退问题排查与修复报告

## 现象
- APK 能正常下载、安装到手机
- 点击启动后直接闪退，连游戏界面都没进去
- logcat 常见错误：`GdxRuntimeException: File not found: game/frames.json` 或 `UnsatisfiedLinkError: libgdx.so`

## 根因分析（3 个致命问题）

### 1. 最严重：assets 目录未打包进 APK（直接导致闪退）
**位置**：`android/build.gradle.kts`

原配置：
```kotlin
android {
  // 没有 sourceSets 配置
}
```

Android Gradle Plugin 默认 assets 目录是 `src/main/assets`，但本项目实际资源放在 `android/assets/game/...`。

- `core` 模块的 `Gdx.files.internal("game/frames.json")` 在 Android 上对应 `assets/game/frames.json`
- 由于未配置 `assets.srcDirs("assets")`，打包时完全不会把 `android/assets` 打进 APK
- `Assets.load()` 第一行就抛 `File not found`，`GameScreen.show()` 崩溃，`AndroidLauncher` 未捕获 -> 闪退

**证据**：
```
ls android/assets/game -> 存在
ls android/src/main/assets -> 不存在
ls android/src/main/jniLibs -> 不存在（生成目录）
```

### 2. 次严重：native 库打包不稳定
`copyNatives` 任务把 `gdx-platform` jar 里的 `libgdx.so` 解压到 `src/main/jniLibs/<abi>/`，但：
- 原任务只处理 `armeabi-v7a` 和 `arm64-v8a`，且没有日志
- `preBuild.dependsOn` 可能在某些 AGP 版本下晚于 `mergeJniLibFolders`
- 如果 `libgdx.so` 没进 APK，启动时 `System.loadLibrary("gdx")` 报 `UnsatisfiedLinkError` 直接闪退

### 3. 防御性不足：音频/资源加载无容错 + 无崩溃日志
- `SfxBank.load()` 和 `GdxAudioBus.musicLayer()` 直接 `getValue()`，文件缺失就崩
- `Assets.load()` 没有日志，闪退后用户无法提供有效 logcat
- `AndroidLauncher` 没有 `UncaughtExceptionHandler`，崩溃信息丢失

## 修复方案

### A. `android/build.gradle.kts` 核心修复
```kotlin
android {
  packaging {
    jniLibs.useLegacyPackaging = true // 兼容 Android 6+
  }
  sourceSets {
    getByName("main") {
      assets.srcDirs("assets") // 关键：把 android/assets 打进 APK
      jniLibs.srcDirs("src/main/jniLibs")
    }
  }
}
```
- `versionCode` 3 -> 4, `versionName` 0.2.1-p1 -> 0.2.2-p1-fix
- `copyNatives` 增强：
  - 支持 `x86_64`, `x86` 兜底
  - 遍历 jar 内所有 `.so` 条目，兼容不同打包结构
  - 增加详细日志 `println`
  - 增加 `merge*JniLibFolders` 依赖，确保在合并前完成

### B. `AndroidLauncher.kt` 加固
- 全局 `UncaughtExceptionHandler` 打印 `assets/game` 列表到 logcat
- 启动前自检 `assets.list("game")` 是否包含 `frames.json`
- `useGL30 = false` 显式指定，避免部分设备 GLES3 兼容问题
- 所有初始化包在 try-catch 并打 Log

### C. `Assets.kt` 加固
- `load()` 开始列出 `game/` 目录内容，快速定位打包问题
- 每个关键文件 `exists()` 检查，缺失时抛带提示的 `RuntimeException`
- `Texture` 加载单独 try-catch
- `drawAnim` 增加空检查，防止 `frames` 未初始化时崩溃

### D. `GdxAudio.kt` 加固
- `SfxBank.load()` 检查 `file.exists()`，缺失跳过而非崩溃
- `get()` 改为可空 `get(): Sound?`
- `sfx()` 播放前判空并 try-catch
- `musicLayer()` 同样检查存在性

### E. `MistboundGame.kt` / `GameScreen.kt` 加固
- `create()` / `show()` 增加 Log 和 try-catch
- 明确日志 tag：`Mistbound`, `MistboundAssets`, `MistboundAudio`, `MistboundCrash`, `MistboundFPS`

### F. `proguard-rules.pro` 加固
- 增加 `-keep` for `gdx.backends.android`, `g2d`, `audio`
- 保留构造方法，防止混淆导致反射失败

### G. CI 防回归
在 `.github/workflows/build-apk.yml` 增加校验步骤：
```bash
unzip -l APK | grep assets/game/frames.json
unzip -l APK | grep libgdx.so
```
缺失直接 fail job，避免再次发布坏包。

## 验证方法（真机）
1. `adb logcat -s Mistbound MistboundAssets MistboundAudio MistboundCrash MistboundFPS`
2. 启动游戏，应看到：
   ```
   I/Mistbound: has game/frames.json in assets? true
   I/MistboundAssets: === Assets.load() start ===
   I/MistboundAssets: game/ dir contains ...
   I/MistboundAssets: Frames loaded: X anims
   I/MistboundAssets: Atlas loaded: 1 pages, N regions
   I/MistboundAssets: === Assets.load() SUCCESS ===
   ```
3. 若仍闪退，查看 `E/MistboundCrash` 堆栈，会打印 `assets/game listing` 帮助定位

## 后续建议
- 将 `android/assets` 迁移到标准 `android/src/main/assets`，避免配置错误（或保留配置但文档化）
- 考虑使用 `gdx-liftoff` 生成的标准项目结构，AAR 自动带 so，无需手写 `copyNatives`
- 在 `GameScreen` 增加加载界面 + 进度条，资源加载失败显示错误页而非直接崩溃
- 添加 Firebase Crashlytics 或类似崩溃上报

---
修复版本：`RELEASE_VERSION` V2 -> V3, `versionCode` 4

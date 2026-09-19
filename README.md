# 《雾境遗章》Mistbound Relics

像素横版动作肉鸽（对标《死亡细胞》）。LibGDX + Kotlin + KTX，60fps 固定时间步，APK ≤ 25MB。

- 设计文档：docs/GDD.md · 工程规范：docs/ENGINEERING.md · 变更记录：docs/CHANGELOG.md
- **取包入口：GitHub Releases 页**（每次合并 main 自动出包：APK V2/V3/…）
- 本地开发：`./gradlew :core:test :android:assembleDebug`
- 素材管线：`python3 tools/pipeline/sprite_pipeline.py && python3 tools/pipeline/gen_audio.py`
  （确定性生成，产物已入库；CI 不运行管线）

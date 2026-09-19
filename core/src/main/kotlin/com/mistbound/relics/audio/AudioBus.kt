package com.mistbound.relics.audio

/**
 * 全部音效 ID（P1）。对应 android/assets/game/audio/sfx/<file>.ogg，
 * 与 core/src/main/resources/audio_manifest.json（tools/pipeline/gen_audio.py 生成）一致，
 * AudioManifestTest 校验两者同步。
 */
enum class SfxId(val file: String) {
    UI_CLICK("ui_click"),
    UI_CONFIRM("ui_confirm"),
    JUMP("jump"),
    ROLL("roll"),
    SLASH_1("slash_1"),
    SLASH_2("slash_2"),
    SLASH_3("slash_3"),
    CHARGE_READY("charge_ready"),
    CHARGE_RELEASE("charge_release"),
    HIT_1("hit_1"),
    HIT_2("hit_2"),
    HIT_CRIT("hit_crit"),
    ENEMY_HIT("enemy_hit"),
    HURT("hurt"),
    DIE_HERO("die_hero"),
    ENEMY_DIE("enemy_die"),
    PICKUP("pickup"),
    PORTAL("portal"),
}

/**
 * 单一音频总线（ENGINEERING P1：AudioBus + 音效对象池 + BGM 层淡入淡出）。
 * 实现侧：Gdx SFX 池 + Music 层（runtime/GdxAudio.kt）；测试侧：计数 Fake。
 */
interface AudioBus {
    /** 播放音效。vol 0..1，pitch 0.5..1.5（变奏），pan -1..1。 */
    fun sfx(id: SfxId, vol: Float = 1f, pitch: Float = 1f, pan: Float = 0f)

    /** BGM 层切换（0..4 = 楼层-1）；层间淡入淡出。 */
    fun musicLayer(index: Int)

    /** 每帧驱动音量渐变。 */
    fun update(dt: Float)

    fun stopAll()
}

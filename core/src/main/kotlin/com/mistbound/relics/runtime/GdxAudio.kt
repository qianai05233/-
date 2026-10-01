package com.mistbound.relics.runtime

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.mistbound.relics.audio.AudioBus
import com.mistbound.relics.audio.SfxId

/** 音效库：启动加载全部 OGG（约 0.3MB），播放走对象池节流。修复：缺失文件时跳过而非崩溃 */
class SfxBank {
    private val sounds = HashMap<SfxId, Sound>(32)

    fun load() {
        if (sounds.isNotEmpty()) return
        for (id in SfxId.entries) {
            val file = Gdx.files.internal("game/audio/sfx/${id.file}.ogg")
            if (!file.exists()) {
                Gdx.app.error("MistboundAudio", "SFX missing: ${file.path()}, skip")
                continue
            }
            try {
                sounds[id] = Gdx.audio.newSound(file)
                Gdx.app.log("MistboundAudio", "SFX loaded: ${id.file}")
            } catch (e: Exception) {
                Gdx.app.error("MistboundAudio", "Failed to load SFX ${id.file}", e)
            }
        }
        Gdx.app.log("MistboundAudio", "SfxBank loaded ${sounds.size}/${SfxId.entries.size}")
    }

    fun get(id: SfxId): Sound? = sounds[id]

    fun dispose() {
        sounds.values.forEach {
            try { it.dispose() } catch (e: Exception) { /* ignore */ }
        }
        sounds.clear()
    }
}

/**
 * Gdx 音频总线实现：
 * - SFX：同 ID 最小间隔 30ms + 每帧全局语音上限（软性对象池节流，防爆音）。
 * - BGM：5 层循环；层间淡入淡出（0.8/s），淡出完成后 pause 释放解码。
 */
class GdxAudioBus(private val bank: SfxBank, private val layers: Int = 5) : AudioBus {

    private val musics = arrayOfNulls<Music>(layers)
    private val volumes = FloatArray(layers)
    private val lastPlay = HashMap<SfxId, Float>(32)
    private var clock = 0f
    private var voices = 0
    private var layer = -1

    var musicVolume = 0.7f

    override fun sfx(id: SfxId, vol: Float, pitch: Float, pan: Float) {
        if (clock - (lastPlay[id] ?: -1f) < 0.03f) return   // 同 ID 节流
        if (voices >= MAX_VOICES_PER_FRAME) return
        lastPlay[id] = clock
        voices++
        try {
            val sound = bank.get(id) ?: run {
                Gdx.app.error("MistboundAudio", "SFX not loaded, skip play: $id")
                return
            }
            sound.play(vol.coerceIn(0f, 1f), pitch.coerceIn(0.5f, 1.5f), pan.coerceIn(-1f, 1f))
        } catch (e: Exception) {
            Gdx.app.error("MistboundAudio", "Failed to play SFX $id", e)
        }
    }

    override fun musicLayer(index: Int) {
        require(index in 0 until layers) { "BGM 层越界: $index" }
        layer = index
        if (musics[index] == null) {
            val file = Gdx.files.internal("game/audio/music/fog_$index.ogg")
            if (!file.exists()) {
                Gdx.app.error("MistboundAudio", "BGM missing: ${file.path()}")
                return
            }
            try {
                musics[index] = Gdx.audio.newMusic(file).apply {
                    isLooping = true
                    volume = 0f
                }
                Gdx.app.log("MistboundAudio", "BGM layer $index loaded")
            } catch (e: Exception) {
                Gdx.app.error("MistboundAudio", "Failed to load BGM $index", e)
                return
            }
        }
        try {
            if (musics[index]!!.isPlaying.not() && volumes[index] <= 0.01f) musics[index]!!.play()
        } catch (e: Exception) {
            Gdx.app.error("MistboundAudio", "Failed to play BGM $index", e)
        }
    }

    override fun update(dt: Float) {
        clock += dt
        voices = 0
        val fade = (dt * 0.8f)
        for (i in 0 until layers) {
            val m = musics[i] ?: continue
            val target = if (i == layer) musicVolume else 0f
            volumes[i] = if (volumes[i] < target) minOf(target, volumes[i] + fade)
            else maxOf(target, volumes[i] - fade)
            m.volume = volumes[i]
            if (volumes[i] <= 0.01f) {
                if (m.isPlaying) m.pause()
            } else if (!m.isPlaying) {
                m.play()
            }
        }
    }

    override fun stopAll() {
        musics.forEach { it?.stop() }
        layer = -1
    }

    fun dispose() {
        stopAll()
        musics.forEach { it?.dispose() }
    }

    companion object {
        const val MAX_VOICES_PER_FRAME = 10
    }
}

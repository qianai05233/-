package com.mistbound.relics.events

import com.mistbound.relics.audio.SfxId

/**
 * 模拟层→表现层事件（纯数据，可单测）。每个固定步长开始时清空收集列表。
 */
sealed class GameEvent {
    data class Sfx(val id: SfxId, val vol: Float = 1f, val pitch: Float = 1f) : GameEvent()
    data class Shake(val amount: Float) : GameEvent()
    data class Hitstop(val seconds: Float) : GameEvent()
    data class Vfx(val anim: String, val x: Float, val y: Float, val flip: Boolean, val scale: Float = 1f) : GameEvent()
    data class Number(val x: Float, val y: Float, val text: String, val crit: Boolean) : GameEvent()
}

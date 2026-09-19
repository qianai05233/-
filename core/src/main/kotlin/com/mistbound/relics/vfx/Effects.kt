package com.mistbound.relics.vfx

/**
 * 一次性动画特效池（剑气/命中星芒/血雾/碎晶/冲刺残迹…effects_vfx_sheet）。
 * 纯逻辑：play 时传入动画时长（由 frames.json duration 查得），到期回收。
 */
class Effects(private val cap: Int = 48) {

    data class Fx(
        val anim: String, var x: Float, var y: Float,
        val flip: Boolean, val scale: Float, var t: Float, val duration: Float,
    ) {
        val alive: Boolean get() = t < duration
    }

    val items = ArrayList<Fx>(cap)

    fun play(anim: String, x: Float, y: Float, flip: Boolean, duration: Float, scale: Float = 1f) {
        if (duration <= 0f) return
        if (items.size >= cap) items.removeAt(0)
        items.add(Fx(anim, x, y, flip, scale, 0f, duration))
    }

    fun update(dt: Float) {
        val it = items.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            if (!f.alive) it.remove()
        }
    }

    fun clear() = items.clear()
}

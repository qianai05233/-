package com.mistbound.relics.vfx

import com.mistbound.relics.Config

/**
 * 伤害飘字池（白字深描边；暴击放大——参考图 1 HUD 规范）。纯逻辑。
 */
class DamageNumbers(private val cap: Int = 32) {

    data class Num(
        var x: Float, var y: Float, var text: String,
        var crit: Boolean, var life: Float, var vx: Float, var vy: Float,
    ) {
        val alpha: Float get() = (life / Config.DMG_NUMBER_LIFE).coerceIn(0f, 1f)
    }

    val items = ArrayList<Num>(cap)

    fun spawn(x: Float, y: Float, text: String, crit: Boolean) {
        if (items.size >= cap) items.removeAt(0)
        items.add(Num(x, y, text, crit, Config.DMG_NUMBER_LIFE, (Math.random().toFloat() - 0.5f) * 14f, Config.DMG_NUMBER_RISE))
    }

    fun spawnDamage(x: Float, y: Float, dmg: Int, crit: Boolean) = spawn(x, y, "-$dmg", crit)

    fun update(dt: Float) {
        val it = items.iterator()
        while (it.hasNext()) {
            val n = it.next()
            n.life -= dt
            n.x += n.vx * dt
            n.y += n.vy * dt
            n.vy -= 40f * dt
            if (n.life <= 0f) it.remove()
        }
    }

    fun clear() = items.clear()
}

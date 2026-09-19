package com.mistbound.relics.combat

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 轴对齐命中/受击盒（世界像素，y 向上，x/y 为左下角）。 */
data class Hitbox(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun overlaps(o: Hitbox): Boolean =
        x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y

    companion object {
        /** 以脚底中心 (cx, footY) 与半宽半高构造。 */
        fun centered(cx: Float, footY: Float, halfW: Float, halfH: Float) =
            Hitbox(cx - halfW, footY, halfW * 2f, halfH)
    }
}

/** 面向 -> 前方攻击盒：从脚底中心出发，reach 前伸、高度 h，底部抬升 lift。 */
fun attackHitbox(cx: Float, footY: Float, facing: Int, reach: Float, h: Float, lift: Float): Hitbox {
    val x0 = if (facing > 0) cx + 2f else cx - 2f - reach
    return Hitbox(x0, footY + lift, reach, h)
}

/** 蓄力攻击盒更大。 */
fun chargeHitbox(cx: Float, footY: Float, facing: Int): Hitbox =
    attackHitbox(cx, footY, facing, reach = 46f, h = 26f, lift = 0f)

/** 击退方向速度。 */
fun knockback(facingToTarget: Int, base: Float, crit: Boolean): Float = facingToTarget * base * if (crit) 1.5f else 1f

/** 伤害计算：base + 暴击倍率（GDD 8：暴击 ×1.75）。 */
fun damage(base: Int, crit: Boolean, chargeMult: Float = 1f): Int =
    max(1, (base * chargeMult * (if (crit) com.mistbound.relics.Config.CRIT_MULTIPLIER else 1f)).toInt())

/** 两实体水平距离（脚底中心）。 */
fun horizontalDist(ax: Float, bx: Float): Float = abs(ax - bx)

fun clamp(v: Float, lo: Float, hi: Float): Float = min(hi, max(lo, v))

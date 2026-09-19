package com.mistbound.relics.enemy

import com.mistbound.relics.Config
import com.mistbound.relics.audio.SfxId
import com.mistbound.relics.combat.Hitbox
import com.mistbound.relics.combat.clamp
import com.mistbound.relics.events.GameEvent
import com.mistbound.relics.player.Player
import kotlin.math.abs
import kotlin.math.sign

/**
 * F1 占位小怪：兜帽幽魂（近战扑击）/ 蓝焰怪（快速突进）。飞行，不受重力。
 * 状态机：漂浮待机 → 追击 → 前摇(0.25s) → 扑击(active 0.18s) → 收招 → 追击；受击硬直；死亡消散。
 */
class Enemy(
    val kind: Kind,
    x: Float,
    y: Float,
) {
    enum class Kind(val hp: Int, val speed: Float, val halfW: Float, val halfH: Float, val dmg: Int) {
        WRAITH(30, 34f, 9f, 12f, Config.ENEMY_CONTACT_DMG),
        FLAME(20, 55f, 8f, 10f, Config.ENEMY_LUNGE_DMG),
    }

    enum class State { IDLE, CHASE, WINDUP, LUNGE, RECOVER, HURT, DIE, GONE }

    var x: Float = x
        private set
    var y: Float = y
        private set
    var prevX: Float = x
        private set
    var prevY: Float = y
        private set
    var vx: Float = 0f
        private set
    var vy: Float = 0f
        private set
    var facing: Int = -1
        private set
    var state: State = State.IDLE
        private set
    var hp: Int = kind.hp
        private set

    var hitFlash: Float = 0f
        private set
    var animClock: Float = 0f
        private set
    var alpha: Float = 1f
        private set
    val dead: Boolean get() = state == State.DIE || state == State.GONE

    private var timer = 0f
    private var lungeDir = 1f

    val events = ArrayList<GameEvent>(4)

    val cx: Float get() = x
    val cy: Float get() = y + kind.halfH

    fun hurtbox(): Hitbox = Hitbox(x - kind.halfW, y, kind.halfW * 2f, kind.halfH * 2f)

    /** 扑击伤害盒（仅 LUNGE 生效）。 */
    fun strikebox(): Hitbox? = if (state == State.LUNGE) Hitbox(x - kind.halfW - 2f, y, (kind.halfW + 2f) * 2f, kind.halfH * 2f) else null

    fun animId(): String = when (kind) {
        Kind.WRAITH -> when (state) {
            State.HURT -> "wraith_hurt"
            State.DIE, State.GONE -> "wraith_die"
            else -> "wraith_idle"
        }
        Kind.FLAME -> when (state) {
            State.WINDUP, State.LUNGE -> "flame_burst"
            State.DIE, State.GONE -> "flame_die"
            else -> "flame_idle"
        }
    }

    fun update(dt: Float, target: Player?, level: Level) {
        prevX = x
        prevY = y
        animClock += dt
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        events.clear()

        if (state == State.GONE) return
        if (state == State.DIE) {
            timer -= dt
            alpha = (timer / DIE_TIME).coerceIn(0f, 1f)
            if (timer <= 0f) state = State.GONE
            return
        }

        val dist = target?.let { abs(it.cx - x) } ?: Float.MAX_VALUE
        val dy = target?.let { (it.footY + 8f) - (y + kind.halfH) } ?: 0f

        when (state) {
            State.IDLE -> {
                approachV(0f, dt)
                if (target != null && !target.dead && dist < AGGRO) state = State.CHASE
            }
            State.CHASE -> {
                if (target == null || target.dead || dist > DEAGGRO) {
                    state = State.IDLE
                } else {
                    val dir = (target.cx - x).sign
                    facing = if (dir != 0f) dir.toInt() else facing
                    approachV(dir * kind.speed, dt)
                    vy += (dy.sign * 20f - vy) * (dt * 4f)
                    if (dist < STRIKE_RANGE && abs(dy) < 22f && timer <= 0f) {
                        state = State.WINDUP
                        timer = WINDUP_TIME
                    }
                }
            }
            State.WINDUP -> {
                timer -= dt
                approachV(0f, dt)
                if (target != null) lungeDir = (target.cx - x).sign.takeIf { it != 0f } ?: facing.toFloat()
                if (timer <= 0f) {
                    state = State.LUNGE
                    timer = LUNGE_TIME
                    vx = lungeDir * LUNGE_SPEED
                    events.add(GameEvent.Vfx("vfx_dash", x, y + kind.halfH, lungeDir < 0))
                    if (kind == Kind.FLAME) events.add(GameEvent.Sfx(SfxId.SLASH_2, 0.5f, 0.7f))
                }
            }
            State.LUNGE -> {
                timer -= dt
                vx = lungeDir * LUNGE_SPEED * (0.4f + 0.6f * (timer / LUNGE_TIME))
                vy *= (1f - dt * 3f)
                if (timer <= 0f) {
                    state = State.RECOVER
                    timer = RECOVER_TIME
                }
            }
            State.RECOVER -> {
                timer -= dt
                approachV(0f, dt)
                if (timer <= 0f) {
                    state = State.CHASE
                    timer = COOLDOWN
                }
            }
            State.HURT -> {
                timer -= dt
                vx *= (1f - dt * 6f)
                if (timer <= 0f) state = State.CHASE
            }
            else -> {}
        }

        // 漂浮 + 关卡边界钳制（幽灵穿平台）
        y += kotlin.math.sin(animClock * 3f) * 4f * dt
        x += vx * dt
        y += vy * dt
        x = clamp(x, 16f, level.width - 16f)
        y = clamp(y, 42f, 200f)
    }

    private fun approachV(target: Float, dt: Float) {
        val d = target - vx
        vx += d * (dt * 5f)
    }

    /** 被玩家攻击命中。返回是否结算（硬直内免伤，但致死伤害必结算）。 */
    fun hurt(dmg: Int, crit: Boolean, fromX: Float): Boolean {
        if (dead) return false
        if (state == State.HURT && timer > 0.12f && hp > dmg) return false
        hp -= dmg
        hitFlash = 0.09f
        val dir = if (cx < fromX) -1f else 1f
        vx = dir * (if (crit) 150f else 90f)
        if (hp <= 0) {
            state = State.DIE
            timer = DIE_TIME
            events.add(GameEvent.Sfx(SfxId.ENEMY_DIE, 1f, 0.9f + Math.random().toFloat() * 0.2f))
            events.add(GameEvent.Vfx("vfx_shards", x, y + kind.halfH, false))
        } else {
            state = State.HURT
            timer = HURT_STUN
        }
        return true
    }

    fun respawn(nx: Float, ny: Float) {
        x = nx; y = ny; prevX = nx; prevY = ny
        vx = 0f; vy = 0f
        hp = kind.hp
        state = State.IDLE
        alpha = 1f
        timer = 0f
    }

    companion object {
        const val AGGRO = 160f
        const val DEAGGRO = 280f
        const val STRIKE_RANGE = 30f
        const val WINDUP_TIME = 0.25f
        const val LUNGE_TIME = 0.18f
        const val RECOVER_TIME = 0.45f
        const val COOLDOWN = 0.6f
        const val HURT_STUN = 0.2f
        const val DIE_TIME = 0.4f
        const val LUNGE_SPEED = 130f
    }
}

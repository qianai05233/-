package com.mistbound.relics.player

import com.mistbound.relics.Config
import com.mistbound.relics.audio.SfxId
import com.mistbound.relics.combat.Hitbox
import com.mistbound.relics.combat.attackHitbox
import com.mistbound.relics.combat.chargeHitbox
import com.mistbound.relics.events.GameEvent
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.world.Level
import com.mistbound.relics.world.Solid
import kotlin.math.abs

/**
 * 主角固定步长物理/战斗状态机。
 * P0：跑/跳/滚 + 土狼/跳缓冲/翻滚无敌帧（单测锁定，勿动参数）。
 * P1：攻击三连击 / 蓄力 / 受击 / 死亡 / hitstop & 屏震事件（GDD 2）。
 * 位置单位为虚拟像素（480x270 世界），y 向上，(x,y) 为碰撞盒左下角。
 */
class Player(
    var x: Float = 60f,
    var y: Float = 40f,
) {
    enum class State { IDLE, RUN, AIRBORNE, ROLLING, ATTACK, CHARGE, HURT, DEAD }

    var prevX: Float = x
        private set
    var prevY: Float = y
        private set
    var vx: Float = 0f
        private set
    var vy: Float = 0f
        private set
    var facing: Int = 1
        private set
    var state: State = State.IDLE
        private set
    var onGround: Boolean = false
        private set

    /** 翻滚无敌帧剩余（>0 即无敌）。 */
    var iframeTime: Float = 0f
        private set

    /** 受击硬直/无敌（GDD：受击后短暂无敌）。 */
    var hurtIframe: Float = 0f
        private set

    /** P0 占位字段保留（总计攻击键计时，调试用）。 */
    var attackTime: Float = 0f
        private set

    val width = 14f
    val height = 24f

    var hp: Int = Config.PLAYER_MAX_HP
        private set
    val maxHp: Int get() = Config.PLAYER_MAX_HP
    var dead: Boolean = false
        private set

    // ---- 攻击状态 ----
    var attackCombo: Int = 0          // 0..2 = 三连击；9 = 蓄力释放
        private set
    var attackT: Float = 0f
        private set
    private var attackHitConsumed = false
    private var activeFired = false   // 本段攻击已进入 active（音效/剑气）
    private var cancelWindow = 0f     // 命中后可取消窗口（GDD 0.15s）
    private var bufferedNext = false  // 攻击中按攻击 → 连段缓冲
    private var comboEndTimer = 0f    // 收招后连击维持窗
    var chargeT: Float = 0f
        private set
    var chargeReady: Boolean = false
        private set

    /** 本步产生的事件（渲染层在步后消费）。 */
    val events = ArrayList<GameEvent>(8)

    private var coyote: Float = 0f
    private var jumpBuffer: Float = 0f
    private var rollTime: Float = 0f
    private var hurtT: Float = 0f

    fun renderX(alpha: Float): Float = prevX + (x - prevX) * alpha
    fun renderY(alpha: Float): Float = prevY + (y - prevY) * alpha

    val cx: Float get() = x + width / 2f
    val footY: Float get() = y

    private val attacking: Boolean get() = state == State.ATTACK
    private val attackTotal: Float
        get() = if (attackCombo == 9) 0.06f + 0.12f + 0.18f
        else Config.ATTACK_STARTUP[attackCombo] + Config.ATTACK_ACTIVE[attackCombo] + Config.ATTACK_RECOVERY[attackCombo]

    private fun attackPhaseIsActive(): Boolean {
        val t = attackT
        return if (attackCombo == 9) t >= 0.06f && t < 0.06f + 0.12f
        else t >= Config.ATTACK_STARTUP[attackCombo] && t < Config.ATTACK_STARTUP[attackCombo] + Config.ATTACK_ACTIVE[attackCombo]
    }

    private fun attackPhaseIsRecovery(): Boolean {
        val t = attackT
        val activeEnd = if (attackCombo == 9) 0.18f
        else Config.ATTACK_STARTUP[attackCombo] + Config.ATTACK_ACTIVE[attackCombo]
        return t >= activeEnd
    }

    /** 动画 ID（渲染层采样 frames.json）。 */
    fun animId(): String = when (state) {
        State.DEAD -> "hero_death"
        State.HURT -> "hero_hurt"
        State.ROLLING -> "hero_roll"
        State.CHARGE -> "hero_charge_atk"
        State.ATTACK -> when {
            attackCombo == 9 -> "hero_attack3"
            attackCombo == 0 -> "hero_attack1"
            attackCombo == 1 -> "hero_attack2"
            else -> "hero_attack3"
        }
        State.AIRBORNE -> "hero_air"
        State.RUN -> "hero_run"
        State.IDLE -> "hero_idle"
    }

    /** 当前动画应推进的时间基准（攻击用攻击计时，其余用累计步时）。 */
    var animClock: Float = 0f
        private set

    /** 当前状态持续秒数（状态切换清零；roll 等动画采样基准）。 */
    var stateClock: Float = 0f
        private set
    private var lastState: State = State.IDLE

    // ---------------------------------------------------------------- update

    fun update(dt: Float, input: InputSnapshot, level: Level) {
        prevX = x
        prevY = y
        events.clear()
        animClock += dt

        coyote = if (onGround) Config.COYOTE_TIME else (coyote - dt).coerceAtLeast(0f)
        jumpBuffer = if (input.jumpPressed) Config.JUMP_BUFFER else (jumpBuffer - dt).coerceAtLeast(0f)
        iframeTime = (iframeTime - dt).coerceAtLeast(0f)
        hurtIframe = (hurtIframe - dt).coerceAtLeast(0f)
        attackTime = (attackTime - dt).coerceAtLeast(0f)
        cancelWindow = (cancelWindow - dt).coerceAtLeast(0f)
        comboEndTimer = (comboEndTimer - dt).coerceAtLeast(0f)
        if (input.attackPressed) attackTime = 0.25f

        if (!dead && input.moveX != 0f) facing = if (input.moveX > 0f) 1 else -1

        when {
            dead -> updateDead(dt, input, level)
            hurtT > 0f -> updateHurt(dt, level)
            state == State.ROLLING -> updateRoll(dt, input, level)
            state == State.CHARGE -> updateCharge(dt, input, level)
            attacking -> updateAttack(dt, input, level)
            else -> updateFree(dt, input, level)
        }

        moveAndCollide(dt, level)

        if (state != lastState) {
            lastState = state
            stateClock = 0f
        } else {
            stateClock += dt
        }
        if (state != State.ROLLING && state != State.ATTACK && state != State.CHARGE &&
            state != State.HURT && state != State.DEAD
        ) {
            state = when {
                !onGround -> State.AIRBORNE
                abs(vx) > 1f -> State.RUN
                else -> State.IDLE
            }
        }
    }

    // 自由态：跑/跳/滚/起手攻击
    private fun updateFree(dt: Float, input: InputSnapshot, level: Level) {
        // 翻滚
        if (input.rollPressed && onGround) {
            startRoll()
            return
        }
        // 连击窗口内的再按
        if (input.attackPressed) {
            if (onGround || state == State.AIRBORNE) {
                startAttack(if (comboEndTimer > 0f) (lastCombo + 1).coerceAtMost(2) else 0)
                return
            }
        }
        // 水平控制
        val target = input.moveX * if (abs(input.moveX) > 0.6f) Config.RUN_SPEED else Config.WALK_SPEED
        val accel = if (onGround) Config.GROUND_ACCEL else Config.AIR_ACCEL
        vx = approach(vx, target, accel * dt)

        // 跳跃：缓冲 + 土狼
        if (jumpBuffer > 0f && (onGround || coyote > 0f)) {
            vy = Config.JUMP_VELOCITY
            jumpBuffer = 0f
            coyote = 0f
            onGround = false
            events.add(GameEvent.Sfx(SfxId.JUMP, vol = 0.8f, pitch = 0.95f + Math.random().toFloat() * 0.1f))
        }
        // 跳缓：提前松手削减上升速度
        if (!input.jumpHeld && vy > 0f) {
            vy -= Config.JUMP_CUT_DECEL * dt
        }
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL
    }

    // 翻滚（0.30s，前 0.10s 加速；无敌帧 = 全程）
    private fun updateRoll(dt: Float, input: InputSnapshot, level: Level) {
        rollTime -= dt
        val boosted = rollTime > Config.ROLL_IFRAMES - Config.ROLL_ACCEL_WINDOW
        vx = facing * Config.ROLL_SPEED * if (boosted) Config.ROLL_BOOST else 1f
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL
        if (rollTime <= 0f) state = if (onGround) State.IDLE else State.AIRBORNE
    }

    private fun startRoll() {
        state = State.ROLLING
        rollTime = Config.ROLL_IFRAMES
        iframeTime = Config.ROLL_IFRAMES
        events.add(GameEvent.Sfx(SfxId.ROLL, vol = 0.9f))
    }

    // 攻击三段
    private fun updateAttack(dt: Float, input: InputSnapshot, level: Level) {
        attackT += dt
        if (input.attackPressed) bufferedNext = true

        // 命中后可取消窗口：翻滚/下一击（GDD 2）
        if (cancelWindow > 0f) {
            if (input.rollPressed && onGround) {
                bufferedNext = false
                startRoll()
                return
            }
            if (input.attackPressed && attackCombo < 2) {
                startAttack(attackCombo + 1)
                return
            }
        }
        // 蓄力：收招段按住攻击且在地面 → 进入蓄力
        if (attackPhaseIsRecovery() && input.attackHeld && onGround && attackCombo != 9) {
            startCharge()
            return
        }
        // active 起始：音效 + 剑气
        if (!activeFired && attackPhaseIsActive()) {
            activeFired = true
            val (sfx, anim) = when (attackCombo) {
                9 -> SfxId.CHARGE_RELEASE to "vfx_slash_b"
                2 -> SfxId.SLASH_3 to "vfx_slash_m"
                1 -> SfxId.SLASH_2 to "vfx_slash_m"
                else -> SfxId.SLASH_1 to "vfx_slash_s"
            }
            events.add(GameEvent.Sfx(sfx, vol = 1f, pitch = 0.95f + Math.random().toFloat() * 0.1f))
            events.add(GameEvent.Vfx(anim, cx + facing * 16f, y + 10f, facing < 0, scale = if (attackCombo == 9) 1.25f else 1f))
        }
        // 前移（攻击惯性）
        if (attackPhaseIsActive()) {
            vx = approach(vx, facing * Config.ATTACK_LUNGE, Config.GROUND_ACCEL * dt)
        } else {
            vx = approach(vx, 0f, Config.GROUND_ACCEL * dt)
        }
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL

        if (attackT >= attackTotal) {
            lastCombo = attackCombo
            if (bufferedNext && attackCombo < 2 && onGround) {
                startAttack(attackCombo + 1)
            } else {
                comboEndTimer = Config.COMBO_WINDOW
                state = if (onGround) State.IDLE else State.AIRBORNE
            }
            bufferedNext = false
        }
    }

    private fun startAttack(combo: Int) {
        state = State.ATTACK
        attackCombo = if (combo == 9) 9 else combo.coerceIn(0, 2)
        attackT = 0f
        attackHitConsumed = false
        activeFired = false
        cancelWindow = 0f
    }

    /** 攻击 active 窗口：每次攻击只产出一次命中盒。 */
    fun tryConsumeAttackHitbox(): Hitbox? {
        if (!attacking || !attackPhaseIsActive() || attackHitConsumed) return null
        attackHitConsumed = true
        return if (attackCombo == 9) chargeHitbox(cx, footY, facing)
        else attackHitbox(cx, footY, facing, reach = 30f, h = 20f, lift = 2f)
    }

    /** 命中确认：开启可取消窗口 + 微 hitstop 由命中方分级发出。 */
    fun onAttackConfirmed() {
        cancelWindow = Config.ATTACK_CANCEL_WINDOW
    }

    val isChargeAttack: Boolean get() = attacking && attackCombo == 9

    // 蓄力
    private fun startCharge() {
        state = State.CHARGE
        chargeT = 0f
        chargeReady = false
        vx = 0f
    }

    private fun updateCharge(dt: Float, input: InputSnapshot, level: Level) {
        chargeT += dt
        vx = approach(vx, 0f, Config.GROUND_ACCEL * dt)
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL
        if (!chargeReady && chargeT >= Config.CHARGE_TIME) {
            chargeReady = true
            events.add(GameEvent.Sfx(SfxId.CHARGE_READY, vol = 0.9f))
        }
        when {
            input.attackReleased -> {           // 放开 → 蓄力斩
                startAttack(9)
            }
            input.rollPressed -> {              // 翻滚可取消蓄力
                startRoll()
            }
            !onGround -> {                      // 走落空 → 取消
                state = State.AIRBORNE
            }
        }
    }

    // 受击 / 死亡
    private fun updateHurt(dt: Float, level: Level) {
        hurtT -= dt
        vx = approach(vx, 0f, Config.GROUND_ACCEL * 0.6f * dt)
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL
        if (hurtT <= 0f) state = if (onGround) State.IDLE else State.AIRBORNE
    }

    private fun updateDead(dt: Float, input: InputSnapshot, level: Level) {
        vx = approach(vx, 0f, Config.GROUND_ACCEL * 0.3f * dt)
        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL
    }

    fun canBeHit(): Boolean = !dead && iframeTime <= 0f && hurtIframe <= 0f && state != State.DEAD

    /** 受击：击退 + 硬直 + 受击无敌 + 屏震/血雾事件。返回是否命中。 */
    fun hurt(dmg: Int, fromX: Float): Boolean {
        if (!canBeHit()) return false
        hp -= dmg
        hurtIframe = Config.HURT_IFRAMES
        hurtT = Config.HURT_STUN
        state = State.HURT
        val dir = if (cx < fromX) -1 else 1
        vx = dir * 130f
        if (hp <= 0) {
            hp = 0
            dead = true
            state = State.DEAD
            events.add(GameEvent.Sfx(SfxId.DIE_HERO, vol = 1f))
            events.add(GameEvent.Shake(0.7f))
            events.add(GameEvent.Vfx("vfx_blood", cx, y + 12f, false))
        } else {
            events.add(GameEvent.Sfx(SfxId.HURT, vol = 1f))
            events.add(GameEvent.Shake(0.35f))
            events.add(GameEvent.Vfx("vfx_blood", cx, y + 12f, false))
        }
        return true
    }

    /** 重开一局（P1：死亡后重置）。 */
    fun respawn(nx: Float = 60f, ny: Float = 40f) {
        x = nx; y = ny; prevX = nx; prevY = ny
        vx = 0f; vy = 0f
        hp = maxHp; dead = false
        state = State.IDLE
        iframeTime = 0f; hurtIframe = 0f; hurtT = 0f
        attackT = 0f; attackCombo = 0; chargeT = 0f; chargeReady = false
        cancelWindow = 0f; bufferedNext = false; lastCombo = 0
    }

    var lastCombo: Int = 0
        private set

    // ---------------------------------------------------------------- 碰撞

    private fun moveAndCollide(dt: Float, level: Level) {
        // X 轴
        x += vx * dt
        for (s in level.solids) {
            if (overlaps(s)) {
                x = if (vx > 0f) s.x - width else s.x + s.w
                vx = 0f
            }
        }
        // Y 轴
        onGround = false
        y += vy * dt
        for (s in level.solids) {
            if (overlaps(s)) {
                if (vy <= 0f) {
                    y = s.y + s.h
                    onGround = true
                } else {
                    y = s.y - height
                }
                vy = 0f
            }
        }
        // 贴地探测：重力未推进的帧（如蓄力起手）站在表面时保持接地判定
        if (!onGround && vy <= 0f) {
            for (s in level.solids) {
                val top = s.y + s.h
                if (top <= y + 0.01f && y - top <= 0.6f && x < s.x + s.w && x + width > s.x) {
                    onGround = true
                    break
                }
            }
        }
    }

    private fun overlaps(s: Solid): Boolean =
        x < s.x + s.w && x + width > s.x && y < s.y + s.h && y + height > s.y

    private fun approach(v: Float, target: Float, maxDelta: Float): Float = when {
        v < target -> minOf(v + maxDelta, target)
        else -> maxOf(v - maxDelta, target)
    }
}

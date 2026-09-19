package com.mistbound.relics.player

import com.mistbound.relics.Config
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.world.Level
import com.mistbound.relics.world.Solid
import kotlin.math.abs

/**
 * 主角固定步长物理/状态机（P0：跑/跳/滚 + 土狼/跳缓冲/翻滚无敌帧）。
 * 位置单位为虚拟像素（480x270 世界），y 向上，(x,y) 为碰撞盒左下角。
 */
class Player(
    var x: Float = 60f,
    var y: Float = 40f,
) {
    enum class State { IDLE, RUN, AIRBORNE, ROLLING }

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

    /** 攻击占位计时（P1 接入完整战斗）。 */
    var attackTime: Float = 0f
        private set

    private var coyote: Float = 0f
    private var jumpBuffer: Float = 0f
    private var rollTime: Float = 0f

    val width = 14f
    val height = 24f

    fun renderX(alpha: Float): Float = prevX + (x - prevX) * alpha
    fun renderY(alpha: Float): Float = prevY + (y - prevY) * alpha

    fun update(dt: Float, input: InputSnapshot, level: Level) {
        prevX = x
        prevY = y

        coyote = if (onGround) Config.COYOTE_TIME else (coyote - dt).coerceAtLeast(0f)
        jumpBuffer = if (input.jumpPressed) Config.JUMP_BUFFER else (jumpBuffer - dt).coerceAtLeast(0f)
        iframeTime = (iframeTime - dt).coerceAtLeast(0f)
        attackTime = (attackTime - dt).coerceAtLeast(0f)
        if (input.attackPressed) attackTime = 0.25f

        if (input.moveX != 0f) facing = if (input.moveX > 0f) 1 else -1

        // 翻滚（地面发起，0.30s，前 0.10s 加速）
        if (state == State.ROLLING) {
            rollTime -= dt
            val boosted = rollTime > Config.ROLL_IFRAMES - Config.ROLL_ACCEL_WINDOW
            vx = facing * Config.ROLL_SPEED * if (boosted) Config.ROLL_BOOST else 1f
            if (rollTime <= 0f) {
                state = if (onGround) State.IDLE else State.AIRBORNE
            }
        } else if (input.rollPressed && onGround) {
            state = State.ROLLING
            rollTime = Config.ROLL_IFRAMES
            iframeTime = Config.ROLL_IFRAMES
        }

        // 水平控制（翻滚中不接管）
        if (state != State.ROLLING) {
            val target = input.moveX * if (abs(input.moveX) > 0.6f) Config.RUN_SPEED else Config.WALK_SPEED
            val accel = if (onGround) Config.GROUND_ACCEL else Config.AIR_ACCEL
            vx = approach(vx, target, accel * dt)
        }

        // 跳跃：缓冲 + 土狼
        if (state != State.ROLLING && jumpBuffer > 0f && (onGround || coyote > 0f)) {
            vy = Config.JUMP_VELOCITY
            jumpBuffer = 0f
            coyote = 0f
            onGround = false
        }
        // 跳缓：提前松手削减上升速度
        if (!input.jumpHeld && vy > 0f) {
            vy -= Config.JUMP_CUT_DECEL * dt
        }

        vy -= Config.GRAVITY * dt
        if (vy < -Config.MAX_FALL) vy = -Config.MAX_FALL

        moveAndCollide(dt, level)

        state = when {
            state == State.ROLLING -> State.ROLLING
            !onGround -> State.AIRBORNE
            abs(vx) > 1f -> State.RUN
            else -> State.IDLE
        }
    }

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
    }

    private fun overlaps(s: Solid): Boolean =
        x < s.x + s.w && x + width > s.x && y < s.y + s.h && y + height > s.y

    private fun approach(v: Float, target: Float, maxDelta: Float): Float = when {
        v < target -> minOf(v + maxDelta, target)
        else -> maxOf(v - maxDelta, target)
    }
}

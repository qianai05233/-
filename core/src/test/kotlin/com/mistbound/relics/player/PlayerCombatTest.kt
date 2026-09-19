package com.mistbound.relics.player

import com.mistbound.relics.Config
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.world.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P1 战斗状态机：连击/取消窗/蓄力/受击/死亡。手感参数来自 GDD 2（Config 锁定）。
 */
class PlayerCombatTest {

    private val dt = Config.FIXED_DT
    private val floor = Level.placeholder()

    private fun tap(p: Player, steps: Int, input: InputSnapshot = InputSnapshot()) {
        repeat(steps) { p.update(dt, input, floor) }
    }

    /** 出生帧恰好贴地不重叠（onGround=false），先空走一步落地再动作（与 P0 测试惯例一致）。 */
    private fun settled(): Player {
        val p = Player(60f, 40f)
        tap(p, 1)
        assertTrue(p.onGround, "settle 后应落地")
        return p
    }

    @Test
    fun `attack starts and advances through phases`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true), floor)
        assertEquals(Player.State.ATTACK, p.state)
        assertEquals(0, p.attackCombo)
        tap(p, 4) // 0.067s < 前摇 0.08
        assertEquals(null, p.tryConsumeAttackHitbox(), "前摇期内无命中盒")
        p.update(dt, InputSnapshot(), floor) // 0.083s ≥ 前摇
        assertTrue(p.tryConsumeAttackHitbox() != null)
    }

    @Test
    fun `attack buffers next combo`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true), floor)
        tap(p, 11) // 0.183s：进入收招段（0.18 后）
        p.update(dt, InputSnapshot(attackPressed = true), floor) // 缓冲下一击
        assertEquals(true, p.state == Player.State.ATTACK)
        tap(p, 10) // 攻击结束（0.28s+）→ 连段 2
        assertEquals(1, p.attackCombo, "收招中按攻击应接第二段")
        assertEquals(Player.State.ATTACK, p.state)
    }

    @Test
    fun `confirmed hit opens roll cancel window`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true), floor)
        tap(p, 8) // 0.133s：active 中
        p.onAttackConfirmed()
        // 命中后 0.15s 内可取消入翻滚
        p.update(dt, InputSnapshot(rollPressed = true), floor)
        assertEquals(Player.State.ROLLING, p.state, "命中后应可取消入翻滚")
    }

    @Test
    fun `roll cancel not allowed before hit confirmation`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true), floor)
        tap(p, 2) // 前摇早期，cancelWindow 未开启
        p.update(dt, InputSnapshot(rollPressed = true), floor)
        assertEquals(Player.State.ATTACK, p.state, "前摇不可取消（GDD 2）")
    }

    @Test
    fun `charge flow ready and release`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true, attackHeld = true), floor)
        tap(p, 12, InputSnapshot(attackHeld = true)) // 进入收招
        assertEquals(Player.State.CHARGE, p.state)
        assertFalse(p.chargeReady)
        tap(p, 20, InputSnapshot(attackHeld = true)) // ~0.33s
        p.update(dt, InputSnapshot(attackHeld = true), floor) // ≥0.35s
        assertTrue(p.chargeReady, "0.35s 蓄力就绪")
        p.update(dt, InputSnapshot(attackReleased = true), floor)
        assertEquals(9, p.attackCombo, "放开应触发蓄力斩")
        assertEquals(Player.State.ATTACK, p.state)
    }

    @Test
    fun `charge cancels into roll`() {
        val p = settled()
        p.update(dt, InputSnapshot(attackPressed = true, attackHeld = true), floor)
        tap(p, 12, InputSnapshot(attackHeld = true))
        assertEquals(Player.State.CHARGE, p.state)
        p.update(dt, InputSnapshot(rollPressed = true), floor)
        assertEquals(Player.State.ROLLING, p.state, "翻滚应可取消蓄力")
    }

    @Test
    fun `hurt applies knockback stun and iframes`() {
        val p = settled()
        val hpBefore = p.hp
        assertTrue(p.hurt(20, fromX = p.cx + 40f))
        assertEquals(hpBefore - 20, p.hp)
        assertEquals(Player.State.HURT, p.state)
        assertTrue(p.vx < 0f, "应向远离伤害源方向击退")
        assertEquals(Config.HURT_IFRAMES, p.hurtIframe)
        // 硬直期间不吃输入移动（击退被摩擦衰减，而非被输入加速）
        val vx = p.vx
        p.update(dt, InputSnapshot(moveX = 1f), floor)
        assertTrue(p.vx < 0f && p.vx > vx, "硬直中击退只应衰减: vx=${p.vx}")
        // 无敌期内二次受击无效
        assertFalse(p.hurt(20, fromX = p.cx + 40f))
    }

    @Test
    fun `death and respawn reset`() {
        val p = settled()
        assertTrue(p.hurt(9999, fromX = p.cx + 10f))
        assertTrue(p.dead)
        assertEquals(Player.State.DEAD, p.state)
        assertEquals(0, p.hp)
        p.respawn()
        assertEquals(p.maxHp, p.hp)
        assertFalse(p.dead)
        assertEquals(Player.State.IDLE, p.state)
    }

    @Test
    fun `p0 feel params untouched`() {
        // 既有手感基线不被 P1 改动（改参数须同步 PlayerPhysicsTest）
        assertEquals(0.08f, Config.COYOTE_TIME)
        assertEquals(0.12f, Config.JUMP_BUFFER)
        assertEquals(0.30f, Config.ROLL_IFRAMES)
        assertEquals(0.10f, Config.ROLL_ACCEL_WINDOW)
        assertEquals(0.15f, Config.ATTACK_CANCEL_WINDOW)
        assertEquals(0.06f, Config.HITSTOP_SMALL)
        assertEquals(0.10f, Config.HITSTOP_MINIBOSS)
        assertEquals(0.14f, Config.HITSTOP_FINALBOSS)
    }
}

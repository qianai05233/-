package com.mistbound.relics.combat

import com.mistbound.relics.Config
import com.mistbound.relics.enemy.Enemy
import com.mistbound.relics.events.GameEvent
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.player.Player
import com.mistbound.relics.world.Level
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatTest {

    private val level = Level.placeholder()
    private val dt = Config.FIXED_DT

    /** 出生帧恰好贴地不重叠（onGround=false），先空走一步落地再动作（与 P0 测试惯例一致）。 */
    private fun settle(p: Player) = p.update(dt, InputSnapshot(), level)

    private fun attackPlayer(x: Float = 60f, y: Float = 40f): Player {
        val p = Player(x, y)
        settle(p)
        p.update(dt, InputSnapshot(attackPressed = true), level)
        // 推进到 active 窗口（combo0 前摇 0.08s）
        repeat(5) { p.update(dt, InputSnapshot(), level) }
        return p
    }

    @Test
    fun `hitbox overlap math`() {
        val a = Hitbox(0f, 0f, 10f, 10f)
        assertTrue(a.overlaps(Hitbox(5f, 5f, 10f, 10f)))
        assertTrue(!a.overlaps(Hitbox(11f, 0f, 10f, 10f)))
        assertTrue(!a.overlaps(Hitbox(0f, -11f, 10f, 10f)))
    }

    @Test
    fun `player attack resolves damage numbers shake and hitstop`() {
        val p = attackPlayer()
        val hb = p.tryConsumeAttackHitbox()
        assertTrue(hb != null, "active 窗口应产出命中盒")
        assertTrue(p.tryConsumeAttackHitbox() == null, "同一攻击只产出一次命中盒")

        val e = Enemy(Enemy.Kind.WRAITH, 85f, 40f)
        val events = ArrayList<GameEvent>()
        val hits = Combat.resolvePlayerAttack(hb!!, p, listOf(e), Random(7), events)

        assertEquals(1, hits.size)
        val dmgSet = setOf(
            Config.ATTACK_DAMAGE[0],
            (Config.ATTACK_DAMAGE[0] * Config.CRIT_MULTIPLIER).toInt(),
        )
        assertTrue(hits[0].dmg in dmgSet, "伤害应为 24 或暴击 42，实际 ${hits[0].dmg}")
        assertTrue(events.any { it is GameEvent.Number && it.text == "-${hits[0].dmg}" })
        assertTrue(events.any { it is GameEvent.Hitstop && it.seconds >= Config.HITSTOP_SMALL })
        assertTrue(events.any { it is GameEvent.Shake })
        assertTrue(events.any { it is GameEvent.Sfx })
        assertEquals(Enemy.Kind.WRAITH.hp - hits[0].dmg, e.hp)
    }

    @Test
    fun `crit multiplier uses gdd value`() {
        // 暴击 ×1.75（GDD 8）
        assertEquals((24 * Config.CRIT_MULTIPLIER).toInt(), damage(24, crit = true))
        assertEquals(24, damage(24, crit = false))
    }

    @Test
    fun `charge attack is stronger and wider`() {
        val p = Player(60f, 40f)
        settle(p)
        // 起手 → 收招段按住攻击 → 蓄力 → 就绪后放开 → 蓄力斩
        p.update(dt, InputSnapshot(attackPressed = true, attackHeld = true), level)
        repeat(12) { p.update(dt, InputSnapshot(attackHeld = true), level) } // 0.2s 进入收招
        assertEquals(Player.State.CHARGE, p.state, "收招段按住攻击应进入蓄力")
        repeat(21) { p.update(dt, InputSnapshot(attackHeld = true), level) } // 0.35s
        assertTrue(p.chargeReady, "蓄力应就绪")
        p.update(dt, InputSnapshot(attackReleased = true), level)
        assertEquals(9, p.attackCombo)
        assertTrue(p.isChargeAttack)

        val hb = run {
            repeat(4) { p.update(dt, InputSnapshot(), level) }
            p.tryConsumeAttackHitbox()
        }
        assertTrue(hb != null)
        assertTrue(hb!!.w > 40f, "蓄力攻击盒应更宽: w=${hb.w}")
        val e = Enemy(Enemy.Kind.WRAITH, 92f, 40f)
        val events = ArrayList<GameEvent>()
        val hits = Combat.resolvePlayerAttack(hb, p, listOf(e), Random(3), events)
        assertTrue(hits.first().dmg >= Config.CHARGE_ATTACK_DAMAGE, "蓄力斩伤害应 ≥ ${Config.CHARGE_ATTACK_DAMAGE}")
        assertTrue(events.any { it is GameEvent.Hitstop && it.seconds >= Config.HITSTOP_SMALL })
    }

    @Test
    fun `combo damage table matches gdd tier values`() {
        assertTrue(Config.ATTACK_DAMAGE.contentEquals(listOf(24, 26, 36).toIntArray()))
    }

    @Test
    fun `enemy contact damage is blocked while iframed`() {
        val p = attackPlayer()
        val e = Enemy(Enemy.Kind.WRAITH, p.cx + 6f, 40f)
        val events = ArrayList<GameEvent>()
        val before = p.hp
        val hit1 = Combat.contact(p, listOf(e), events)
        assertEquals(1, hit1, "身体接触应命中玩家一次")
        assertTrue(p.hp < before)
        // 受击无敌期内不再命中
        val hit2 = Combat.contact(p, listOf(e), events)
        assertEquals(0, hit2)
    }
}

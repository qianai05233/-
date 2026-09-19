package com.mistbound.relics.enemy

import com.mistbound.relics.Config
import com.mistbound.relics.combat.Hitbox
import com.mistbound.relics.player.Player
import com.mistbound.relics.world.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnemyAiTest {

    private val dt = Config.FIXED_DT
    private val level = Level.placeholder()

    @Test
    fun `idle until player in aggro range then chase`() {
        val e = Enemy(Enemy.Kind.WRAITH, 400f, 60f)
        val far = Player(100f, 40f)
        repeat(60) { e.update(dt, far, level) }
        assertEquals(Enemy.State.IDLE, e.state, "玩家距离 300 > 160 应保持待机")

        val near = Player(380f, 40f)
        e.update(dt, near, level)
        assertEquals(Enemy.State.CHASE, e.state)
        val x0 = e.x
        repeat(30) { e.update(dt, near, level) }
        assertTrue(e.x > x0, "应朝玩家移动")
    }

    @Test
    fun `windup then lunge then recover`() {
        val e = Enemy(Enemy.Kind.WRAITH, 100f, 60f)
        val p = Player(104f, 40f)
        // 进入 STRIKE_RANGE 后前摇
        repeat(10) { e.update(dt, p, level) }
        assertEquals(Enemy.State.WINDUP, e.state, "近距离应进入前摇")
        // 前摇期内无伤害盒
        assertEquals(null, e.strikebox())
        repeat(16) { e.update(dt, p, level) } // 0.267s > 0.25
        assertEquals(Enemy.State.LUNGE, e.state)
        assertTrue(e.strikebox() != null, "扑击期应有伤害盒")
        val hit = e.strikebox()!!
        val pb = Hitbox(p.x, p.y, p.width, p.height)
        assertTrue(hit.overlaps(pb), "贴身扑击应覆盖玩家")
        repeat(20) { e.update(dt, p, level) } // 扑击 0.18 + 收招开始
        assertTrue(e.state == Enemy.State.RECOVER || e.state == Enemy.State.CHASE)
    }

    @Test
    fun `hurt stuns then dies and fades out`() {
        val e = Enemy(Enemy.Kind.WRAITH, 100f, 60f)
        val p = Player(300f, 40f)
        assertTrue(e.hurt(10, crit = false, fromX = 110f))
        assertEquals(Enemy.Kind.WRAITH.hp - 10, e.hp)
        assertEquals(Enemy.State.HURT, e.state)
        assertTrue(e.hitFlash > 0f)
        assertTrue(e.vx < 0f, "受击应被击退")

        // 硬直中免伤，但致死伤害必结算
        assertFalse(e.hurt(5, crit = false, fromX = 110f))
        assertTrue(e.hurt(999, crit = true, fromX = 110f))
        assertEquals(Enemy.State.DIE, e.state)
        assertTrue(e.dead)
        repeat(30) { e.update(dt, p, level) } // 0.5s > 0.4
        assertEquals(Enemy.State.GONE, e.state)
    }

    @Test
    fun `flame is faster frailer variant`() {
        val w = Enemy(Enemy.Kind.WRAITH, 0f, 60f)
        val f = Enemy(Enemy.Kind.FLAME, 0f, 60f)
        assertTrue(f.kind.speed > w.kind.speed)
        assertTrue(f.kind.hp < w.kind.hp)
    }

    @Test
    fun `enemies stay inside level bounds`() {
        val e = Enemy(Enemy.Kind.WRAITH, 100f, 60f)
        val p = Player(2300f, 40f) // 引诱向右边界
        repeat(600) { e.update(dt, p, level) }
        assertTrue(e.x <= level.width - 16f + 0.5f, "x=${e.x} 超界")
        assertTrue(e.x >= 16f - 0.5f)
    }
}

package com.mistbound.relics.player

import com.mistbound.relics.Config
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.world.Level
import com.mistbound.relics.world.Solid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerPhysicsTest {

    private val dt = Config.FIXED_DT
    private val floorOnly = Level(listOf(Solid(-200f, 0f, 2600f, 40f)))
    private val platformLevel = Level(
        listOf(
            Solid(-200f, 0f, 2600f, 40f),
            Solid(300f, 84f, 90f, 12f),
        )
    )

    private fun Player.step(n: Int, input: InputSnapshot, level: Level = floorOnly) {
        repeat(n) { update(dt, input, level) }
    }

    private val runRight = InputSnapshot(moveX = 1f)
    private val neutral = InputSnapshot()

    @Test
    fun `coyote time allows jump shortly after leaving ledge`() {
        val p = Player(380f, 96f)
        // 跑下平台边缘
        var steps = 0
        while (p.onGround && steps < 240) {
            p.update(dt, runRight, platformLevel)
            steps++
        }
        assertTrue(!p.onGround, "player should have left the platform")
        // 离地 4 步 (~0.067s < 0.08s) 后起跳，应成功
        p.step(4, neutral, platformLevel)
        p.update(dt, InputSnapshot(jumpPressed = true, jumpHeld = true), platformLevel)
        assertTrue(p.vy > 0f, "coyote jump should fire, vy=${p.vy}")
    }

    @Test
    fun `coyote time expires after 0.08s`() {
        val p = Player(380f, 96f)
        var steps = 0
        while (p.onGround && steps < 240) {
            p.update(dt, runRight, platformLevel)
            steps++
        }
        p.step(7, neutral, platformLevel) // ~0.117s > 0.08s
        p.update(dt, InputSnapshot(jumpPressed = true, jumpHeld = true), platformLevel)
        assertTrue(p.vy <= 0f, "jump must not fire after coyote expiry, vy=${p.vy}")
    }

    @Test
    fun `jump buffer fires on landing when pressed early`() {
        // 第一遍：找落地点
        val probe = Player(60f, 150f)
        var landing = -1
        for (i in 0 until 600) {
            probe.update(dt, neutral, floorOnly)
            if (probe.onGround) {
                landing = i
                break
            }
        }
        assertTrue(landing > 0, "probe should land")

        // 第二遍：落地前 5 步 (~0.083s < 0.12s) 按下跳跃
        val p = Player(60f, 150f)
        var jumped = false
        for (i in 0 until landing + 3) {
            val input = if (i == landing - 5) {
                InputSnapshot(jumpPressed = true, jumpHeld = true)
            } else {
                InputSnapshot(jumpHeld = true)
            }
            p.update(dt, input, floorOnly)
            if (p.onGround && p.vy > 0f) jumped = true
            if (p.vy > 0f && i > landing - 5) jumped = true
        }
        assertTrue(jumped, "buffered jump should fire on landing")
    }

    @Test
    fun `roll grants 0.30s iframes then expires`() {
        val p = Player(60f, 40f)
        p.step(2, neutral)
        p.update(dt, InputSnapshot(rollPressed = true), floorOnly)
        assertEquals(Player.State.ROLLING, p.state)
        p.step(16, neutral) // ~0.27s，仍在无敌窗口
        assertTrue(p.iframeTime > 0f, "iframes=${p.iframeTime}")
        p.step(6, neutral) // 总 ~0.37s
        assertEquals(0f, p.iframeTime)
        assertTrue(p.state != Player.State.ROLLING)
    }

    @Test
    fun `render interpolation bounded by prev and current`() {
        val p = Player(60f, 40f)
        repeat(120) {
            p.update(dt, runRight, floorOnly)
            val a = p.renderX(0.5f)
            val lo = minOf(p.prevX, p.x)
            val hi = maxOf(p.prevX, p.x)
            assertTrue(a in lo..hi, "interpolated $a outside [$lo,$hi]")
            assertEquals(p.x, p.renderX(1f))
            assertEquals(p.prevX, p.renderX(0f))
        }
    }
}

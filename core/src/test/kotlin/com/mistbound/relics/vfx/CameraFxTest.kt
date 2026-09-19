package com.mistbound.relics.vfx

import com.mistbound.relics.Config
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraFxTest {

    @Test
    fun `trauma decays and offsets stay bounded`() {
        val fx = CameraFx()
        fx.shake(1f)
        repeat(120) {
            fx.update(1f / 60f)
            assertTrue(abs(fx.offsetX) <= Config.SHAKE_OFFSET + 1e-4f, "offsetX=${fx.offsetX}")
            assertTrue(abs(fx.offsetY) <= Config.SHAKE_OFFSET * 0.6f + 1e-4f)
        }
        assertEquals(0f, fx.trauma, 1e-3f, "2s 后 trauma 应衰减到 0")
        assertEquals(0f, fx.offsetX, 1e-3f)
    }

    @Test
    fun `shake amount is clamped to 1`() {
        val fx = CameraFx()
        fx.shake(0.8f)
        fx.shake(0.8f)
        assertEquals(1f, fx.trauma)
    }

    @Test
    fun `hitstop counts down in real time`() {
        val fx = CameraFx()
        fx.requestHitstop(Config.HITSTOP_SMALL)
        fx.requestHitstop(Config.HITSTOP_MINIBOSS)
        assertEquals(Config.HITSTOP_MINIBOSS, fx.hitstop, 1e-6f, "应取更大值")
        repeat(6) { fx.update(1f / 60f) }
        assertEquals(0f, fx.hitstop, 1e-4f)
    }
}

class DamageNumbersTest {

    @Test
    fun `spawn update expire`() {
        val nums = DamageNumbers()
        nums.spawnDamage(10f, 10f, 32, crit = false)
        assertEquals(1, nums.items.size)
        val n = nums.items[0]
        assertEquals("-32", n.text)
        nums.update(Config.DMG_NUMBER_LIFE + 0.1f)
        assertTrue(nums.items.isEmpty(), "到期应回收")
    }

    @Test
    fun `pool cap drops oldest`() {
        val nums = DamageNumbers(cap = 4)
        repeat(10) { nums.spawnDamage(0f, 0f, it + 1, crit = false) }
        assertEquals(4, nums.items.size)
        assertEquals("-7", nums.items.first().text, "应淘汰最旧")
    }
}

class EffectsTest {

    @Test
    fun `one shot effects expire by duration`() {
        val fx = Effects()
        fx.play("vfx_slash_s", 0f, 0f, false, duration = 0.25f)
        fx.update(0.2f)
        assertEquals(1, fx.items.size)
        fx.update(0.1f)
        assertEquals(0, fx.items.size)
    }

    @Test
    fun `zero duration is rejected`() {
        val fx = Effects()
        fx.play("vfx_slash_s", 0f, 0f, false, duration = 0f)
        assertEquals(0, fx.items.size)
    }
}

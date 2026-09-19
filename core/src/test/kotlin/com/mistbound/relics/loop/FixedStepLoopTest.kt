package com.mistbound.relics.loop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedStepLoopTest {

    @Test
    fun `60hz frame yields exactly one step`() {
        val loop = FixedStepLoop()
        var steps = 0
        loop.update(1f / 60f) { steps++ }
        assertEquals(1, steps)
    }

    @Test
    fun `30hz frame yields two steps`() {
        val loop = FixedStepLoop()
        var steps = 0
        loop.update(1f / 30f) { steps++ }
        assertEquals(2, steps)
    }

    @Test
    fun `alpha stays in 0 to 1`() {
        val loop = FixedStepLoop()
        repeat(600) {
            loop.update(0.0167f + (it % 3) * 0.0001f) {}
            assertTrue(loop.alpha in 0f..1f, "alpha=${loop.alpha}")
        }
    }

    @Test
    fun `spike frame is clamped to avoid spiral of death`() {
        val loop = FixedStepLoop()
        var steps = 0
        loop.update(2f) { steps++ }
        assertEquals(15, steps) // 0.25s clamp / (1/60)
    }
}

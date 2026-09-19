package com.mistbound.relics.perf

import com.mistbound.relics.Config
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.loop.FixedStepLoop
import com.mistbound.relics.player.Player
import com.mistbound.relics.world.Level
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 60fps 逻辑验证脚本：模拟 10 分钟（36000 步）× 9 实体，
 * 输出步率日志并断言相对 60Hz 需求的冗余倍数。
 * 真机渲染帧率由 GameScreen 的 FpsMeter 在 logcat 输出（tag: MistboundFPS）。
 */
class PerfHarnessTest {

    @Test
    fun `60hz logic headroom with 9 entities over 10 simulated minutes`() {
        val level = Level.placeholder()
        val players = List(9) { Player(60f + it * 40f, 40f) }
        val loop = FixedStepLoop()

        var tick = 0L
        fun inputFor(i: Int): InputSnapshot {
            val phase = (tick / 60 + i) % 6
            return when (phase) {
                0L -> InputSnapshot(moveX = 1f)
                1L -> InputSnapshot(moveX = -1f)
                2L -> InputSnapshot(jumpPressed = true, jumpHeld = true)
                3L -> InputSnapshot(moveX = 1f, rollPressed = true)
                4L -> InputSnapshot(jumpHeld = true)
                else -> InputSnapshot(moveX = 0.5f, attackPressed = tick % 30 == 0L)
            }
        }

        val steps = 60 * 60 * 10 // 10 模拟分钟
        val start = System.nanoTime()
        repeat(steps) {
            tick++
            for ((i, p) in players.withIndex()) {
                p.update(Config.FIXED_DT, inputFor(i), level)
            }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val stepsPerSec = (steps / (elapsedMs / 1000.0)).toLong()
        val needed = 60L * players.size // 60Hz × 9 实体 每秒所需实体步
        val headroom = stepsPerSec / needed

        println(
            "PERF harness: $steps steps x ${players.size} entities in ${"%.1f".format(elapsedMs)}ms " +
                "=> $stepsPerSec entity-steps/s; 60Hz needs $needed/s -> headroom ${headroom}x"
        )
        assertTrue(headroom >= 10, "logic must have >=10x headroom over 60Hz, got ${headroom}x")
        assertEqualsSteps(loop)
    }

    private fun assertEqualsSteps(loop: FixedStepLoop) {
        // 累加器自检：1/60 帧输入 => 单步
        var n = 0
        loop.update(1f / 60f) { n++ }
        assertTrue(n == 1)
    }
}

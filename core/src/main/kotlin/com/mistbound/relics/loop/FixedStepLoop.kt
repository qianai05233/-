package com.mistbound.relics.loop

import com.mistbound.relics.Config

/**
 * 固定 60Hz 时间步 + 累加器；渲染插值 alpha（GDD 2 / ENGINEERING 3）。
 */
class FixedStepLoop(private val dt: Float = Config.FIXED_DT) {

    private var accumulator = 0f

    /** 上一次 update 消费的模拟步数。 */
    var stepsThisFrame = 0
        private set

    /** 渲染插值系数 [0,1)。 */
    var alpha = 0f
        private set

    /** 累计模拟步数（性能统计用）。 */
    var totalSteps = 0L
        private set

    fun update(frameDelta: Float, step: (Float) -> Unit) {
        val d = if (frameDelta > Config.MAX_FRAME_DELTA) Config.MAX_FRAME_DELTA else frameDelta
        accumulator += d
        stepsThisFrame = 0
        while (accumulator >= dt) {
            step(dt)
            accumulator -= dt
            stepsThisFrame++
            totalSteps++
        }
        alpha = accumulator / dt
    }
}

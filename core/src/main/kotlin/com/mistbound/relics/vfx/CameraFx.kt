package com.mistbound.relics.vfx

import com.mistbound.relics.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 屏震（trauma 平方衰减模型）+ 全局 hitstop 计时（GDD 2）。
 * 纯逻辑：偏移由调用方注入 rng 采样。
 */
class CameraFx(private val rng: Random = Random(0x5C0A7E)) {
    var trauma = 0f
        private set

    /** 当前帧偏移（渲染前更新）。 */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    fun shake(amount: Float) {
        trauma = min(1f, trauma + amount)
    }

    /** hitstop 剩余秒数；>0 时模拟暂停（FixedStepLoop 不推进）。 */
    var hitstop = 0f
        private set

    fun requestHitstop(seconds: Float) {
        hitstop = max(hitstop, seconds)
    }

    /** 每渲染帧调用（hitstop 期间也继续衰减，保证震屏不冻结）。 */
    fun update(delta: Float) {
        if (hitstop > 0f) hitstop = max(0f, hitstop - delta)
        trauma = max(0f, trauma - Config.SHAKE_DECAY * delta)
        val mag = trauma * trauma * Config.SHAKE_OFFSET
        offsetX = mag * (rng.nextFloat() * 2f - 1f)
        offsetY = mag * 0.6f * (rng.nextFloat() * 2f - 1f)
    }
}

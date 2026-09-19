package com.mistbound.relics.vfx

/** 每秒采样一次渲染帧率并回调（真机 60fps 验证日志来源）。 */
class FpsMeter(
    private val windowSeconds: Float = 1f,
    private val onSample: (Int) -> Unit = {},
) {
    private var frames = 0
    private var acc = 0f

    var fps = 0
        private set

    fun tick(delta: Float) {
        frames++
        acc += delta
        if (acc >= windowSeconds) {
            fps = (frames / acc).toInt()
            onSample(fps)
            frames = 0
            acc = 0f
        }
    }
}

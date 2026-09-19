package com.mistbound.relics.input

/**
 * 每个固定步长采样一次的输入快照。
 * pressed 系列为边沿信号（采样后即消费）。
 */
data class InputSnapshot(
    val moveX: Float = 0f,
    val jumpHeld: Boolean = false,
    val jumpPressed: Boolean = false,
    val rollPressed: Boolean = false,
    val attackPressed: Boolean = false,
    val skillQPressed: Boolean = false,
    val skillEPressed: Boolean = false,
)

interface InputSource {
    fun poll(): InputSnapshot
}

/** 测试/回放用脚本输入。 */
class ScriptedInput(private val script: (step: Long) -> InputSnapshot) : InputSource {
    private var step = 0L
    override fun poll(): InputSnapshot = script(step++)
}

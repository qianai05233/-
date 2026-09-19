package com.mistbound.relics.world

/** 轴对齐静态固体。x/y 为左下角，y 向上。 */
data class Solid(val x: Float, val y: Float, val w: Float, val h: Float)

class Level(val solids: List<Solid>) {
    val width: Float = solids.maxOf { it.x + it.w }
    companion object {
        /** P0 占位关卡：长地面 + 两级平台，验证跑/跳/滚与插值。 */
        fun placeholder(): Level = Level(
            listOf(
                Solid(-200f, 0f, 2600f, 40f),
                Solid(300f, 84f, 90f, 12f),
                Solid(440f, 132f, 90f, 12f),
            )
        )
    }
}

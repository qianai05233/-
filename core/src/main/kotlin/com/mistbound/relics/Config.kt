package com.mistbound.relics

/**
 * 全局数值锚点。手感参数取自 docs/GDD.md 第 2 节（硬性规范）。
 */
object Config {
    // 渲染
    const val VIRTUAL_WIDTH = 480f
    const val VIRTUAL_HEIGHT = 270f

    // 循环
    const val FIXED_DT = 1f / 60f
    const val MAX_FRAME_DELTA = 0.25f

    // 手感（GDD 2）
    const val COYOTE_TIME = 0.08f
    const val JUMP_BUFFER = 0.12f
    const val ROLL_IFRAMES = 0.30f
    const val ROLL_ACCEL_WINDOW = 0.10f
    const val ATTACK_CANCEL_WINDOW = 0.15f
    const val HITSTOP_SMALL = 0.06f
    const val HITSTOP_MINIBOSS = 0.10f
    const val HITSTOP_FINALBOSS = 0.14f

    // 移动
    const val WALK_SPEED = 60f
    const val RUN_SPEED = 110f
    const val ROLL_SPEED = 150f
    const val ROLL_BOOST = 1.25f
    const val JUMP_VELOCITY = 250f
    const val GRAVITY = 700f
    const val JUMP_CUT_DECEL = 600f
    const val MAX_FALL = 320f
    const val GROUND_ACCEL = 1400f
    const val AIR_ACCEL = 750f

    // 数值锚点（GDD 8）
    const val HP_F1 = 120
    const val HP_F3 = 160
    const val HP_F5 = 210
    const val CRIT_MULTIPLIER = 1.75f
}

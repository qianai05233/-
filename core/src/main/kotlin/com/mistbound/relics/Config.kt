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

    // ---- P1 战斗（GDD 2）----
    const val COMBO_WINDOW = 0.35f        // 连击维持窗口
    const val CHARGE_TIME = 0.35f         // 蓄力就绪时间
    const val CHARGE_MULT = 2.2f          // 蓄力伤害倍率
    const val HURT_STUN = 0.18f           // 受击硬直
    const val HURT_IFRAMES = 0.5f         // 受击后无敌
    const val CRIT_CHANCE = 0.15f         // 暴击率
    const val ATTACK_LUNGE = 40f          // 攻击前移速度
    const val PLAYER_MAX_HP = HP_F1

    // 攻击三段：前摇/ active / 后摇（秒）与伤害（飘字中值 -32 tier，GDD 8）
    val ATTACK_STARTUP = floatArrayOf(0.08f, 0.07f, 0.10f)
    val ATTACK_ACTIVE = floatArrayOf(0.10f, 0.10f, 0.12f)
    val ATTACK_RECOVERY = floatArrayOf(0.10f, 0.10f, 0.16f)
    val ATTACK_DAMAGE = intArrayOf(24, 26, 36)
    const val CHARGE_ATTACK_DAMAGE = 58   // 24 × 2.2 ≈ 53，取 58（蓄力满档）

    // 屏震 / hitstop 落点由命中目标分级（HITSTOP_*，上方）
    const val SHAKE_DECAY = 1.8f
    const val SHAKE_OFFSET = 6f

    // 飘字
    const val DMG_NUMBER_LIFE = 0.7f
    const val DMG_NUMBER_RISE = 34f

    // 敌人（F1 占位层）
    const val ENEMY_CONTACT_DMG = 8
    const val ENEMY_LUNGE_DMG = 14
}

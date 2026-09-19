package com.mistbound.relics.combat

import com.mistbound.relics.Config
import com.mistbound.relics.audio.SfxId
import com.mistbound.relics.enemy.Enemy
import com.mistbound.relics.events.GameEvent
import com.mistbound.relics.player.Player
import kotlin.random.Random

/**
 * 命中结算（GDD 2/8）：
 * - 暴击 ×1.75（15% 概率，seeded rng 可复现）
 * - hitstop：小怪 60ms（暴击 90ms）
 * - 屏震按伤害分级；飘字白色/暴击金色放大
 */
object Combat {

    data class Hit(val enemy: Enemy, val dmg: Int, val crit: Boolean, val charge: Boolean)

    /** 玩家攻击结算：对每个重叠敌人结算一次。返回命中列表。 */
    fun resolvePlayerAttack(
        hitbox: Hitbox,
        player: Player,
        enemies: List<Enemy>,
        rng: Random,
        events: MutableList<GameEvent>,
    ): List<Hit> {
        if (hitbox == DUMMY) return emptyList()
        val charge = player.isChargeAttack
        val base = if (charge) Config.CHARGE_ATTACK_DAMAGE else Config.ATTACK_DAMAGE[player.attackCombo]
        val hits = ArrayList<Hit>(2)
        for (e in enemies) {
            if (e.dead) continue
            if (!hitbox.overlaps(e.hurtbox())) continue
            val crit = rng.nextFloat() < Config.CRIT_CHANCE
            val dmg = damage(base, crit, if (charge) 1f else 1f)
            if (e.hurt(dmg, crit, player.cx)) {
                hits.add(Hit(e, dmg, crit, charge))
                player.onAttackConfirmed()
                events.add(GameEvent.Number(e.cx, e.cy + 14f, "-$dmg", crit))
                events.add(GameEvent.Vfx("vfx_hit_star", e.cx, e.cy, player.facing < 0, scale = if (crit || charge) 1.3f else 1f))
                events.add(GameEvent.Sfx(if (crit) SfxId.HIT_CRIT else if (charge) SfxId.HIT_2 else SfxId.HIT_1, 1f, 0.92f + rng.nextFloat() * 0.16f))
                events.add(GameEvent.Shake(if (crit) 0.32f else if (charge) 0.4f else 0.18f))
                events.add(GameEvent.Hitstop(if (crit) Config.HITSTOP_SMALL * 1.5f else Config.HITSTOP_SMALL))
            }
        }
        return hits
    }

    /** 敌方接触/扑击结算：返回本步对玩家造成的命中次数。 */
    fun contact(player: Player, enemies: List<Enemy>, events: MutableList<GameEvent>): Int {
        if (player.dead) return 0
        val pb = Hitbox(player.x, player.y, player.width, player.height)
        var hits = 0
        for (e in enemies) {
            if (e.dead) continue
            val strike = e.strikebox() ?: continue
            if (!strike.overlaps(pb)) continue
            if (player.hurt(Config.ENEMY_LUNGE_DMG, e.cx)) hits++
        }
        // 身体接触（非扑击时轻伤害）
        if (hits == 0) {
            for (e in enemies) {
                if (e.dead) continue
                if (e.state == Enemy.State.LUNGE) continue
                if (e.hurtbox().overlaps(pb)) {
                    if (player.hurt(Config.ENEMY_CONTACT_DMG, e.cx)) hits++
                    break
                }
            }
        }
        return hits
    }

    /** 测试/占位哨兵。 */
    val DUMMY = Hitbox(-9999f, -9999f, 0f, 0f)
}

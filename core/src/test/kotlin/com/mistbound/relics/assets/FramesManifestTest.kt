package com.mistbound.relics.assets

import com.badlogic.gdx.utils.JsonReader
import com.mistbound.relics.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * frames.json ↔ atlas pack.json 一致性 + 关键动画校准校验（P1 素材管线）。
 */
class FramesManifestTest {

    private fun readText(vararg candidates: String): String {
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return f.readText()
        }
        error("找不到文件: ${candidates.joinToString()}")
    }

    private val framesJson = readText(
        "../core/src/main/resources/frames.json",
        "core/src/main/resources/frames.json",
        "src/main/resources/frames.json",
    )
    private val packJson = readText(
        "../android/assets/game/atlas/pack.json",
        "android/assets/game/atlas/pack.json",
    )

    private fun manifest(): FramesManifest {
        val v = JsonReader().parse(framesJson)
        val anims = HashMap<String, Anim>()
        val animsV = v.get("anims")
        var a = animsV.child
        while (a != null) {
            val names = ArrayList<String>()
            var f = a.get("frames").child
            while (f != null) {
                names.add(f.asString())
                f = f.next
            }
            val p = a.get("pivot")
            anims[a.name] = Anim(a.name, names, a.getFloat("fps"), a.getBoolean("loop"), p.get(0).asFloat(), p.get(1).asFloat())
            a = a.next
        }
        return FramesManifest(anims)
    }

    private fun regions(): Set<String> {
        val v = JsonReader().parse(packJson)
        val r = v.get("regions")
        val out = HashSet<String>()
        var c = r.child
        while (c != null) {
            out.add(c.name)
            c = c.next
        }
        return out
    }

    @Test
    fun `android runtime copy matches core manifest`() {
        val core = readText(
            "../core/src/main/resources/frames.json",
            "core/src/main/resources/frames.json",
            "src/main/resources/frames.json",
        )
        val android = readText(
            "../android/assets/game/frames.json",
            "android/assets/game/frames.json",
        )
        assertEquals(core, android, "Android assets 中的 frames.json 必须与 core 清单一致")
    }

    @Test
    fun `core anims exist`() {
        val m = manifest()
        for (id in listOf(
            "hero_idle", "hero_run", "hero_roll", "hero_air", "hero_attack1",
            "hero_attack2", "hero_attack3", "hero_charge_atk", "hero_hurt",
            "hero_death", "hero_win", "wraith_idle", "wraith_hurt", "wraith_die",
            "vfx_slash_s", "vfx_slash_m", "vfx_slash_b", "vfx_hit_star", "vfx_blood",
        )) {
            assertTrue(m.anims.containsKey(id), "缺少动画 $id")
        }
    }

    @Test
    fun `every frame reference exists in atlas regions`() {
        val regions = regions()
        val m = manifest()
        assertTrue(regions.size >= 90, "atlas 区域数异常: ${regions.size}")
        for (anim in m.anims.values) {
            assertTrue(anim.frames.isNotEmpty(), "动画 ${anim.name} 无帧")
            for (f in anim.frames) {
                assertTrue(f in regions, "动画 ${anim.name} 引用了不存在的图集区域: $f")
            }
        }
    }

    @Test
    fun `anim params sane`() {
        val m = manifest()
        for (anim in m.anims.values) {
            assertTrue(anim.fps in 1f..40f, "${anim.name} fps=${anim.fps}")
            assertTrue(anim.pivotX in 0f..1f && anim.pivotY in 0f..1f, "${anim.name} pivot 越界")
        }
    }

    @Test
    fun `roll anim duration matches roll iframe window`() {
        // 帧数校准：翻滚动画时长需贴合 0.30s 无敌帧（±80ms）
        val roll = manifest().anim("hero_roll")
        val dur = roll.duration
        assertTrue(kotlin.math.abs(dur - Config.ROLL_IFRAMES) < 0.08f, "roll 时长 $dur vs 无敌 ${Config.ROLL_IFRAMES}")
    }

    @Test
    fun `non-loop attack anims hold last frame`() {
        val a1 = manifest().anim("hero_attack1")
        assertTrue(!a1.loop && a1.frames.isNotEmpty())
        assertEquals(a1.frames.size - 1, a1.frameIndex(10f), "超时应钳制末帧")
        val idle = manifest().anim("hero_idle")
        val i = idle.frameIndex(idle.duration * 3f + 0.01f)
        assertTrue(i in idle.frames.indices, "循环动画应取模")
    }
}

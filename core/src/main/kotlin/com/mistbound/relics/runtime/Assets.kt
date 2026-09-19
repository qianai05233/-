package com.mistbound.relics.runtime

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.JsonReader
import com.mistbound.relics.assets.Anim
import com.mistbound.relics.assets.FramesManifest

/**
 * 图集 + frames.json 运行时加载（ENGINEERING 4：禁止运行时散图，全走 atlas；
 * Nearest 采样；frames.json 位于 core resources → classpath 读取）。
 */
class Assets {

    lateinit var frames: FramesManifest
        private set

    private val pages = ArrayList<Texture>()
    private val sprites = HashMap<String, Sprite>()
    private val pivots = HashMap<String, Pair<Float, Float>>() // 归一化 pivot（0..1）
    var loaded = false
        private set

    fun load() {
        if (loaded) return
        // frames.json：随 Android assets 打包，避免 Android 运行时 classpath 不可用
        val framesJson = JsonReader().parse(Gdx.files.internal("game/frames.json").readString())
        val anims = HashMap<String, Anim>()
        val animsV = framesJson.get("anims")
        var a = animsV.child
        while (a != null) {
            val name = a.name
            val frameNames = ArrayList<String>()
            var f = a.get("frames").child
            while (f != null) {
                frameNames.add(f.asString())
                f = f.next
            }
            val pivot = a.get("pivot")
            anims[name] = Anim(
                name, frameNames, a.getFloat("fps"), a.getBoolean("loop"),
                pivot.get(0).asFloat(), pivot.get(1).asFloat(),
            )
            a = a.next
        }
        frames = FramesManifest(anims)

        // atlas 页 + 区域（pack.json：android assets）
        val pack = JsonReader().parse(Gdx.files.internal("game/atlas/pack.json").readString())
        var p = pack.get("pages").child
        while (p != null) {
            pages.add(Texture(Gdx.files.internal("game/atlas/" + p.getString("file"))).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            })
            p = p.next
        }
        val regions = pack.get("regions")
        var r = regions.child
        while (r != null) {
            val name = r.name
            val pageIdx = r.getInt("page")
            val region = TextureRegion(pages[pageIdx], r.getInt("x"), r.getInt("y"), r.getInt("w"), r.getInt("h"))
            sprites[name] = Sprite(region)
            val pv = r.get("pivot")
            pivots[name] = Pair(pv.get(0).asFloat() / r.getInt("w"), pv.get(1).asFloat() / r.getInt("h"))
            r = r.next
        }
        loaded = true
    }

    fun sprite(name: String): Sprite? = sprites[name]

    /** 以「pivot 脚点」绘制一帧：x,y = 脚底锚点世界坐标（flip 水平镜像）。 */
    fun drawFrame(batch: SpriteBatch, name: String, x: Float, y: Float, flip: Boolean, alpha: Float = 1f, scale: Float = 1f) {
        val s = sprites[name] ?: return
        val (nx, ny) = pivots[name] ?: Pair(0.5f, 1f)
        val w = s.regionWidth * scale
        val h = s.regionHeight * scale
        val sx = x - (if (flip) (1f - nx) else nx) * w
        val sy = y - ny * h
        s.setFlip(flip, false)
        s.setColor(1f, 1f, 1f, alpha)
        s.setBounds(sx, sy, w, h)
        s.draw(batch)
    }

    /** 绘制动画在 t 时刻的帧。 */
    fun drawAnim(batch: SpriteBatch, anim: Anim, t: Float, x: Float, y: Float, flip: Boolean, alpha: Float = 1f, scale: Float = 1f) {
        val idx = anim.frameIndex(t)
        drawFrame(batch, anim.frames[idx], x, y, flip, alpha, scale)
    }

    fun dispose() {
        pages.forEach { it.dispose() }
        pages.clear()
        sprites.clear()
        loaded = false
    }
}

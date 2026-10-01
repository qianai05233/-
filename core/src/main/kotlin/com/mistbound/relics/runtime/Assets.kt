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
 *
 * 修复：增加详细的日志和容错，防止文件缺失直接闪退无提示。
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

        Gdx.app.log("MistboundAssets", "=== Assets.load() start ===")
        // 列出 internal 根目录，辅助排查打包问题
        try {
            val files = Gdx.files.internal("game").list()
            Gdx.app.log("MistboundAssets", "game/ dir contains ${files.size} entries: ${files.take(20).joinToString { it.name() }}")
        } catch (e: Exception) {
            Gdx.app.error("MistboundAssets", "failed to list game/ dir - assets not packaged! Did you configure assets.srcDirs?", e)
        }

        // frames.json：随 Android assets 打包，避免 Android 运行时 classpath 不可用
        val framesFile = Gdx.files.internal("game/frames.json")
        if (!framesFile.exists()) {
            Gdx.app.error("MistboundAssets", "FATAL: game/frames.json not found! exists=${framesFile.exists()} path=${framesFile.path()}")
            // 尝试备用路径 core/resources
            val fallback = Gdx.files.internal("frames.json")
            if (fallback.exists()) {
                Gdx.app.log("MistboundAssets", "fallback frames.json found at root, using it")
            } else {
                throw RuntimeException("Missing asset: game/frames.json - 请检查 android/build.gradle.kts 的 assets.srcDirs 是否配置为 'assets'")
            }
        }
        Gdx.app.log("MistboundAssets", "Loading frames.json from ${framesFile.path()}")

        val framesJson = try {
            JsonReader().parse(framesFile.readString())
        } catch (e: Exception) {
            Gdx.app.error("MistboundAssets", "Failed to parse frames.json", e)
            throw e
        }

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
        Gdx.app.log("MistboundAssets", "Frames loaded: ${anims.size} anims")

        // atlas 页 + 区域（pack.json：android assets）
        val packFile = Gdx.files.internal("game/atlas/pack.json")
        if (!packFile.exists()) {
            Gdx.app.error("MistboundAssets", "FATAL: game/atlas/pack.json not found!")
            throw RuntimeException("Missing asset: game/atlas/pack.json")
        }
        val pack = try {
            JsonReader().parse(packFile.readString())
        } catch (e: Exception) {
            Gdx.app.error("MistboundAssets", "Failed to parse pack.json", e)
            throw e
        }

        var p = pack.get("pages").child
        var pageCount = 0
        while (p != null) {
            val fileName = p.getString("file")
            val texFile = Gdx.files.internal("game/atlas/$fileName")
            if (!texFile.exists()) {
                Gdx.app.error("MistboundAssets", "Texture page not found: game/atlas/$fileName")
                throw RuntimeException("Missing texture page: $fileName")
            }
            try {
                pages.add(Texture(texFile).apply {
                    setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                })
                pageCount++
                Gdx.app.log("MistboundAssets", "Loaded atlas page: $fileName ${texFile.length()} bytes")
            } catch (e: Exception) {
                Gdx.app.error("MistboundAssets", "Failed to load texture $fileName", e)
                throw e
            }
            p = p.next
        }

        val regions = pack.get("regions")
        var r = regions.child
        var regionCount = 0
        while (r != null) {
            val name = r.name
            val pageIdx = r.getInt("page")
            if (pageIdx < 0 || pageIdx >= pages.size) {
                Gdx.app.error("MistboundAssets", "Invalid pageIdx $pageIdx for region $name")
                r = r.next
                continue
            }
            val region = TextureRegion(pages[pageIdx], r.getInt("x"), r.getInt("y"), r.getInt("w"), r.getInt("h"))
            sprites[name] = Sprite(region)
            val pv = r.get("pivot")
            pivots[name] = Pair(pv.get(0).asFloat() / r.getInt("w"), pv.get(1).asFloat() / r.getInt("h"))
            regionCount++
            r = r.next
        }
        Gdx.app.log("MistboundAssets", "Atlas loaded: $pageCount pages, $regionCount regions")
        loaded = true
        Gdx.app.log("MistboundAssets", "=== Assets.load() SUCCESS ===")
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
        if (!::frames.isInitialized) return
        val animObj = try { frames.anim(anim.name) } catch (e: Exception) { return }
        if (animObj.frames.isEmpty()) return
        val idx = animObj.frameIndex(t).coerceIn(0, animObj.frames.size - 1)
        drawFrame(batch, animObj.frames[idx], x, y, flip, alpha, scale)
    }

    fun dispose() {
        pages.forEach { it.dispose() }
        pages.clear()
        sprites.clear()
        loaded = false
    }
}

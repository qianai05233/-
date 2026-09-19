package com.mistbound.relics.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.mistbound.relics.Config
import com.mistbound.relics.combat.Combat
import com.mistbound.relics.enemy.Enemy
import com.mistbound.relics.events.GameEvent
import com.mistbound.relics.loop.FixedStepLoop
import com.mistbound.relics.player.Player
import com.mistbound.relics.runtime.Assets
import com.mistbound.relics.runtime.GdxAudioBus
import com.mistbound.relics.runtime.SfxBank
import com.mistbound.relics.vfx.CameraFx
import com.mistbound.relics.vfx.DamageNumbers
import com.mistbound.relics.vfx.Effects
import com.mistbound.relics.vfx.FpsMeter
import com.mistbound.relics.world.Level
import kotlin.random.Random

/**
 * P1 主画面：60Hz 模拟 + 插值渲染 + 真像素帧 + 战斗（三连击/蓄力/翻滚无敌/hitstop/屏震/飘字）
 * + SFX/BGM 音频总线 + 波次刷怪（占位层）。
 */
class GameScreen : Screen {

    private val viewport = PixelPerfectViewport(Config.VIRTUAL_WIDTH, Config.VIRTUAL_HEIGHT)
    private val controls = TouchControls(viewport)
    private val loop = FixedStepLoop()
    private val level = Level.placeholder()
    private val player = Player()
    private val assets = Assets()
    private val sfxBank = SfxBank()
    private val audio: GdxAudioBus = GdxAudioBus(sfxBank)

    private val enemies = ArrayList<Enemy>(ENEMY_SPAWNS.size)
    private val combatRng = Random(0x5EED1_2L)

    private val fx = CameraFx()
    private val numbers = DamageNumbers()
    private val effects = Effects()
    private val eventSink = ArrayList<GameEvent>(16)

    private data class Ghost(val frame: String, val x: Float, val y: Float, val flip: Boolean, var life: Float)
    private val ghosts = ArrayList<Ghost>(8)
    private var rollGhostTimer = 0f

    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val hudCamera = com.badlogic.gdx.graphics.OrthographicCamera()

    private var camX = Config.VIRTUAL_WIDTH / 2f
    private var waveIndex = 0
    private var clearTimer = -1f          // >0：本波已清，倒计时刷下一波
    private var winPoseTimer = 0f
    private var comboCount = 0
    private var comboTimer = 0f
    private var bannerText = ""
    private var bannerTimer = 0f

    private val fpsMeter = FpsMeter { fps ->
        Gdx.app.log("MistboundFPS", "fps=$fps stepsLastFrame=${loop.stepsThisFrame}")
    }

    override fun show() {
        Gdx.app.log("GameScreen", "show() start")
        Gdx.input.inputProcessor = controls
        font.data.setScale(0.5f)
        hudCamera.setToOrtho(false, Config.VIRTUAL_WIDTH, Config.VIRTUAL_HEIGHT)
        hudCamera.update()

        try {
            assets.load()
            Gdx.app.log("GameScreen", "assets loaded")
        } catch (e: Exception) {
            Gdx.app.error("GameScreen", "assets.load() failed - will crash without assets", e)
            throw e
        }

        try {
            sfxBank.load()
            Gdx.app.log("GameScreen", "sfxBank loaded")
        } catch (e: Exception) {
            Gdx.app.error("GameScreen", "sfxBank.load() failed, continue without sfx", e)
        }

        try {
            audio.musicLayer(0)
        } catch (e: Exception) {
            Gdx.app.error("GameScreen", "musicLayer failed", e)
        }

        if (enemies.isEmpty()) spawnWave(first = true)
        Gdx.app.log("GameScreen", "show() done, enemies=${enemies.size}")
    }

    // ---------------------------------------------------------------- 模拟

    private fun spawnWave(first: Boolean = false) {
        if (!first) waveIndex++
        val off = if (first) 0f else waveIndex * 70f
        if (first && enemies.isEmpty()) {
            for ((i, s) in ENEMY_SPAWNS.withIndex()) {
                val kind = if (i % 2 == 0) Enemy.Kind.WRAITH else Enemy.Kind.FLAME
                enemies.add(Enemy(kind, s.first, s.second))
            }
        } else {
            for ((i, s) in ENEMY_SPAWNS.withIndex()) {
                enemies[i].respawn(s.first + off, s.second)
            }
        }
        if (!first) {
            banner("第 ${waveIndex + 1} 波")
        }
        clearTimer = -1f
    }

    private fun step(dt: Float) {
        val input = controls.poll()

        player.update(dt, input, level) // dead 状态内部仍推进（死亡动画/重力）
        if (player.dead && input.attackPressed) restartRun()
        for (e in enemies) e.update(dt, player, level)

        // 玩家攻击结算（GDD 2：hitstop/屏震分级在 Combat 发出）
        val hb = player.tryConsumeAttackHitbox()
        if (hb != null) {
            val hits = Combat.resolvePlayerAttack(hb, player, enemies, combatRng, eventSink)
            if (hits.isNotEmpty()) {
                comboCount += hits.size
                comboTimer = 1.2f
            }
        }
        Combat.contact(player, enemies, eventSink)

        // 翻滚残影（0.05s 间隔采样当前帧）
        if (player.state == Player.State.ROLLING) {
            rollGhostTimer -= dt
            if (rollGhostTimer <= 0f) {
                rollGhostTimer = 0.05f
                val anim = assets.frames.anim(player.animId())
                val frame = anim.frames[anim.frameIndex(player.stateClock)]
                ghosts.add(Ghost(frame, player.cx, player.footY, player.facing < 0, 0.22f))
                if (ghosts.size > 10) ghosts.removeAt(0)
            }
        }

        dispatch(player.events)
        for (e in enemies) dispatch(e.events)
        dispatch(eventSink)
        eventSink.clear()

        // 波次循环
        if (!player.dead && enemies.all { it.dead }) {
            if (clearTimer < 0f) {
                clearTimer = 2.5f
                winPoseTimer = 2.5f
                banner("本层清空")
                audio.sfx(com.mistbound.relics.audio.SfxId.UI_CONFIRM, 0.8f)
            } else {
                clearTimer -= dt
                if (clearTimer <= 0f) spawnWave()
            }
        }
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) comboCount = 0
        }
        if (winPoseTimer > 0f) winPoseTimer -= dt
        if (bannerTimer > 0f) bannerTimer -= dt
    }

    private fun dispatch(events: List<GameEvent>) {
        for (ev in events) {
            when (ev) {
                is GameEvent.Sfx -> audio.sfx(ev.id, ev.vol, ev.pitch)
                is GameEvent.Shake -> fx.shake(ev.amount)
                is GameEvent.Hitstop -> fx.requestHitstop(ev.seconds)
                is GameEvent.Vfx -> effects.play(ev.anim, ev.x, ev.y, ev.flip, assets.frames.anim(ev.anim).duration, ev.scale)
                is GameEvent.Number -> numbers.spawn(ev.x, ev.y, ev.text, ev.crit)
            }
        }
    }

    private fun restartRun() {
        player.respawn()
        waveIndex = 0
        spawnWave(first = true)
        numbers.clear()
        effects.clear()
        ghosts.clear()
        comboCount = 0
        banner("重新开始")
        audio.sfx(com.mistbound.relics.audio.SfxId.UI_CONFIRM, 0.8f)
    }

    private fun banner(text: String) {
        bannerText = text
        bannerTimer = 1.6f
    }

    // ---------------------------------------------------------------- 渲染

    override fun render(delta: Float) {
        fpsMeter.tick(delta)
        fx.update(delta) // hitstop 消耗 + 震屏衰减（hitstop 期间继续震）

        if (fx.hitstop <= 0f) {
            loop.update(delta) { dt -> step(dt) }
            numbers.update(delta)
            effects.update(delta)
            val git = ghosts.iterator()
            while (git.hasNext()) {
                val g = git.next()
                g.life -= delta
                if (g.life <= 0f) git.remove()
            }
        }
        audio.update(delta)

        // 相机：跟随 + 前瞻 + 屏震
        val alpha = loop.alpha
        val lookAhead = player.facing * 24f
        val targetX = player.renderX(alpha) + player.width / 2f + lookAhead
        camX += (targetX - camX) * minOf(1f, delta * 6f)
        val half = Config.VIRTUAL_WIDTH / 2f
        camX = camX.coerceIn(half, maxOf(half, level.width - half))
        viewport.camera.position.set(camX + fx.offsetX, Config.VIRTUAL_HEIGHT / 2f + fx.offsetY, 0f)
        viewport.camera.update()

        // 清屏（letterbox 深）
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.06f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // ---- 世界：背景 + 关卡 ----
        viewport.apply()
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawBackground()
        drawLevel()
        shapes.end()

        // ---- 世界：精灵 ----
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        drawGhosts()
        drawEnemies(alpha)
        drawPlayer(alpha)
        drawEffects()
        drawNumbers()
        batch.end()

        // ---- HUD（屏幕空间）----
        shapes.projectionMatrix = hudCamera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawHudShapes()
        controls.draw(shapes)
        shapes.end()

        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        drawHudText()
        batch.end()
    }

    // 视差背景：夜色渐层 + 两层遗迹剪影 + 雾团（占位美术，P2 换场景图）
    private fun drawBackground() {
        val half = Config.VIRTUAL_WIDTH / 2f
        val l = viewport.camera.position.x - half
        shapes.rect(l, 0f, Config.VIRTUAL_WIDTH, 170f, Color(0.05f, 0.07f, 0.14f, 1f), Color(0.05f, 0.07f, 0.14f, 1f), Color(0.10f, 0.10f, 0.22f, 1f), Color(0.10f, 0.10f, 0.22f, 1f))
        shapes.rect(l, 170f, Config.VIRTUAL_WIDTH, 100f, Color(0.10f, 0.10f, 0.22f, 1f), Color(0.10f, 0.10f, 0.22f, 1f), Color(0.07f, 0.08f, 0.18f, 1f), Color(0.07f, 0.08f, 0.18f, 1f))
        // 远景剪影（视差 0.35）
        shapes.color = Color(0.04f, 0.05f, 0.11f, 1f)
        val far = l + (Config.VIRTUAL_WIDTH - camX * 0.35f % 480f)
        for (i in 0..2) {
            val bx = far + i * 190f
            shapes.rect(bx, 150f, 46f, 120f)
            shapes.triangle(bx - 8f, 270f, bx + 23f, 120f, bx + 54f, 270f)
            shapes.rect(bx + 70f, 180f, 30f, 90f)
        }
        // 近景剪影（视差 0.6）
        shapes.color = Color(0.03f, 0.035f, 0.08f, 1f)
        val near = l + (-camX * 0.6f % 360f)
        for (i in 0..1) {
            val bx = near + i * 360f
            shapes.rect(bx, 120f, 26f, 150f)
            shapes.triangle(bx - 14f, 270f, bx + 13f, 190f, bx + 40f, 270f)
        }
        // 雾团
        shapes.color = Color(0.55f, 0.65f, 0.9f, 0.05f)
        val t = com.badlogic.gdx.utils.TimeUtils.millis() / 1000f
        for (i in 0..5) {
            val fx0 = l + ((i * 137f + t * (6f + i) ) % (Config.VIRTUAL_WIDTH + 120f)) - 60f
            shapes.ellipse(fx0, 60f + (i % 3) * 34f, 70f, 18f)
        }
    }

    private fun drawLevel() {
        val half = Config.VIRTUAL_WIDTH / 2f
        for (s in level.solids) {
            if (s.x + s.w < viewport.camera.position.x - half - 8f || s.x > viewport.camera.position.x + half + 8f) continue
            shapes.color = Color(0.12f, 0.13f, 0.24f, 1f)
            shapes.rect(s.x, s.y, s.w, s.h)
            shapes.color = Color(0.36f, 0.40f, 0.58f, 1f)
            shapes.rect(s.x, s.y + s.h - 2f, s.w, 2f)
        }
    }

    private fun drawGhosts() {
        for (g in ghosts) {
            assets.drawFrame(batch, g.frame, g.x, g.y, g.flip, alpha = 0.35f * (g.life / 0.22f))
        }
    }

    private fun drawEnemies(alphaR: Float) {
        for (e in enemies) {
            if (e.state == Enemy.State.GONE) continue
            val anim = assets.frames.anim(e.animId())
            val t = if (e.state == Enemy.State.DIE) 0f else e.animClock
            val bob = if (e.state == Enemy.State.DIE) 0f else kotlin.math.sin(e.animClock * 3f) * 2f
            if (e.hitFlash > 0f) {
                val fname = anim.frames.last() + "_flash"
                if (assets.sprite(fname) != null) {
                    assets.drawFrame(batch, fname, e.cx, e.y + bob, e.facing < 0, e.alpha)
                    continue
                }
            }
            assets.drawAnim(batch, anim, t, e.cx, e.y + bob, e.facing < 0, e.alpha)
        }
    }

    private fun drawPlayer(alphaR: Float) {
        if (player.dead && player.stateClock > 1.2f) return // 死亡动画播完不再画
        val animId = when {
            winPoseTimer > 0f && (player.state == Player.State.IDLE || player.state == Player.State.RUN) -> "hero_win"
            else -> player.animId()
        }
        val anim = assets.frames.anim(animId)
        val t = if (player.state == Player.State.ATTACK) player.attackT else player.stateClock
        val flip = player.facing < 0
        val px = player.renderX(loop.alpha) + player.width / 2f
        val py = player.renderY(loop.alpha)
        // 无敌帧闪烁（翻滚受击闪）
        val blink = player.iframeTime > 0f && (com.badlogic.gdx.utils.TimeUtils.millis() / 60L) % 2L == 0L
        assets.drawAnim(batch, anim, t, px, py, flip, if (blink) 0.55f else 1f)
        // 受击白闪（受击最初 0.1s）
        if (player.hurtIframe > Config.HURT_IFRAMES - 0.1f && !player.dead) {
            val fname = anim.frames[anim.frameIndex(t)] + "_flash"
            if (assets.sprite(fname) != null) assets.drawFrame(batch, fname, px, py, flip)
        }
    }

    private fun drawEffects() {
        for (f in effects.items) {
            val anim = assets.frames.anim(f.anim)
            val flipX = f.flip
            assets.drawAnim(batch, anim, f.t, f.x, f.y, flipX, alpha = 1f, scale = f.scale)
        }
    }

    private fun drawNumbers() {
        for (n in numbers.items) {
            font.data.setScale(if (n.crit) 0.8f else 0.55f)
            val a = n.alpha
            font.color = Color(0.04f, 0.05f, 0.10f, a) // 深描边（阴影近似）
            font.draw(batch, n.text, n.x + 1f, n.y - 1f)
            font.color = if (n.crit) Color(1f, 0.86f, 0.5f, a) else Color(0.95f, 0.96f, 1f, a)
            font.draw(batch, n.text, n.x, n.y)
        }
        font.data.setScale(0.5f)
    }

    private fun drawHudShapes() {
        // 斜切血条（参考图 1：斜切边金属框）
        val bx = 10f
        val by = Config.VIRTUAL_HEIGHT - 16f
        val bw = 110f
        val bh = 8f
        val sk = 5f
        // 底框
        quad(bx, by, bw, bh, sk, Color(0.08f, 0.09f, 0.16f, 0.9f))
        // 血量
        val ratio = (player.hp.toFloat() / player.maxHp).coerceIn(0f, 1f)
        if (ratio > 0f) quad(bx + 1f, by + 1f, (bw - 2f) * ratio, bh - 2f, sk * 0.8f, if (ratio > 0.3f) Color(0.85f, 0.28f, 0.38f, 1f) else Color(1f, 0.45f, 0.3f, 1f))
        // 顶边高光
        quad(bx, by + bh, bw, 1.2f, sk, Color(0.7f, 0.75f, 0.95f, 0.7f))
    }

    private fun quad(x: Float, y: Float, w: Float, h: Float, sk: Float, c: Color) {
        shapes.color = c
        shapes.triangle(x, y, x + w - sk, y, x + w, y + h)
        shapes.triangle(x, y, x + w, y + h, x + sk, y + h)
    }

    private fun drawHudText() {
        font.color = Color(0.95f, 0.96f, 1f, 1f)
        font.data.setScale(0.5f)
        font.draw(batch, "FPS ${fpsMeter.fps}", 4f, Config.VIRTUAL_HEIGHT - 24f)
        font.draw(batch, "HP ${player.hp}/${player.maxHp}", 14f, Config.VIRTUAL_HEIGHT - 19f)
        if (comboCount >= 2) {
            font.data.setScale(0.75f)
            font.draw(batch, "COMBO x$comboCount", 400f, Config.VIRTUAL_HEIGHT - 40f)
            font.data.setScale(0.5f)
        }
        if (bannerTimer > 0f) {
            font.data.setScale(1f)
            font.color = Color(0.95f, 0.96f, 1f, minOf(1f, bannerTimer))
            val w = 12f * bannerText.length
            font.draw(batch, bannerText, (Config.VIRTUAL_WIDTH - w) / 2f, Config.VIRTUAL_HEIGHT - 70f)
            font.data.setScale(0.5f)
        }
        if (player.dead) {
            font.data.setScale(1.2f)
            font.color = Color(0.95f, 0.4f, 0.45f, 1f)
            font.draw(batch, "你已倒下", 190f, 170f)
            font.data.setScale(0.5f)
            font.color = Color(0.8f, 0.82f, 0.95f, 1f)
            font.draw(batch, "按 攻击键 重新开始", 195f, 150f)
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.resize(width, height)
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
        assets.dispose()
        sfxBank.dispose()
        audio.dispose()
    }

    companion object {
        private val ENEMY_SPAWNS = listOf(
            600f to 60f, 780f to 60f, 1000f to 60f,
            1220f to 60f, 1500f to 60f,
        )
    }
}

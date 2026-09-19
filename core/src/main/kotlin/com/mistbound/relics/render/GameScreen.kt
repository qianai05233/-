package com.mistbound.relics.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.mistbound.relics.Config
import com.mistbound.relics.loop.FixedStepLoop
import com.mistbound.relics.player.Player
import com.mistbound.relics.vfx.FpsMeter
import com.mistbound.relics.world.Level

/**
 * P0 主画面：固定 60Hz 模拟 + 渲染插值 + 整数缩放 + 占位胶囊主角 + 触摸五键/摇杆。
 */
class GameScreen : Screen {

    private val viewport = PixelPerfectViewport(Config.VIRTUAL_WIDTH, Config.VIRTUAL_HEIGHT)
    private val controls = TouchControls(viewport)
    private val loop = FixedStepLoop()
    private val level = Level.placeholder()
    private val player = Player()

    private val shapes = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont()

    private val fpsMeter = FpsMeter { fps ->
        Gdx.app.log("MistboundFPS", "fps=$fps stepsLastFrame=${loop.stepsThisFrame}")
    }

    override fun show() {
        Gdx.input.inputProcessor = controls
        font.data.setScale(0.5f)
    }

    override fun resize(width: Int, height: Int) {
        viewport.resize(width, height)
    }

    override fun render(delta: Float) {
        fpsMeter.tick(delta)
        loop.update(delta) { dt ->
            player.update(dt, controls.poll(), level)
        }
        val alpha = loop.alpha

        // 全屏清屏（含 letterbox 黑边）
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.01f, 0.02f, 0.05f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // 地面/平台
        shapes.color = Color(0.10f, 0.12f, 0.22f, 1f)
        for (s in level.solids) {
            shapes.rect(s.x, s.y, s.w, s.h)
        }

        // 主角占位胶囊（翻滚无敌帧闪烁）
        val px = player.renderX(alpha)
        val py = player.renderY(alpha)
        val flicker = player.iframeTime > 0f && (System.nanoTime() / 60_000_000L) % 2L == 0L
        shapes.color = if (flicker) Color(0.54f, 0.71f, 0.97f, 0.45f) else Color(0.79f, 0.80f, 0.87f, 1f)
        shapes.rect(px, py, player.width, player.height - 7f)
        shapes.circle(px + player.width / 2f, py + player.height - 7f, player.width / 2f, 16)

        // 攻击占位光弧
        if (player.attackTime > 0f) {
            shapes.color = Color(0.54f, 0.71f, 0.97f, 0.7f)
            shapes.circle(px + player.width / 2f + player.facing * 14f, py + 12f, 8f, 16)
        }

        // 触控 UI
        controls.draw(shapes)
        shapes.end()

        // HUD：FPS 计数（60fps 验证）
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "FPS ${fpsMeter.fps}", 4f, Config.VIRTUAL_HEIGHT - 4f)
        batch.end()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}

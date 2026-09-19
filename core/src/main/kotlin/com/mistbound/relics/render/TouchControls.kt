package com.mistbound.relics.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.mistbound.relics.Config
import com.mistbound.relics.input.InputSnapshot
import com.mistbound.relics.input.InputSource
import kotlin.math.abs

/**
 * 触屏输入：左半屏虚拟摇杆 + 右侧五键（攻击/跳跃/翻滚/Q/E，GDD 7）。
 * 布局参考 docs/reference/02_scene_hud_spec.md 右下技能槽位置。
 * P1 仍为几何占位绘制，P2 换 ui_hud_kit.png 图集。
 */
class TouchControls(private val viewport: PixelPerfectViewport) : InputProcessor, InputSource {

    private data class Button(val id: String, val x: Float, val y: Float, val r: Float)

    private val buttons = listOf(
        Button("ROLL", 386f, 42f, 15f),
        Button("ATTACK", 420f, 56f, 18f),
        Button("JUMP", 454f, 42f, 15f),
        Button("Q", 402f, 88f, 12f),
        Button("E", 436f, 88f, 12f),
    )

    private val joystickRadius = 26f

    private var joyPointer = -1
    private var joyAnchorX = 0f
    private var joyAnchorY = 0f
    private var joyX = 0f

    private val pointerButton = HashMap<Int, String>()
    private val held = HashSet<String>()
    private val pressed = HashSet<String>()

    // 键盘回退（调试用）
    private var keyLeft = false
    private var keyRight = false
    private var keyJumpHeld = false
    private var keyAttackHeld = false

    private fun screenH(): Float = Gdx.graphics.backBufferHeight.toFloat()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val wx = viewport.toWorldX(screenX.toFloat())
        val wy = viewport.toWorldY(screenY.toFloat(), screenH())
        if (wx < Config.VIRTUAL_WIDTH / 2f && joyPointer == -1) {
            joyPointer = pointer
            joyAnchorX = wx
            joyAnchorY = wy
            joyX = 0f
            return true
        }
        buttons.firstOrNull { (wx - it.x) * (wx - it.x) + (wy - it.y) * (wy - it.y) <= it.r * it.r }?.let {
            pointerButton[pointer] = it.id
            held.add(it.id)
            pressed.add(it.id)
        }
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (pointer == joyPointer) {
            val wx = viewport.toWorldX(screenX.toFloat())
            var dx = wx - joyAnchorX
            if (abs(dx) > joystickRadius) dx = if (dx > 0) joystickRadius else -joystickRadius
            joyX = dx / joystickRadius
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer == joyPointer) {
            joyPointer = -1
            joyX = 0f
        }
        pointerButton.remove(pointer)?.let { held.remove(it) }
        return true
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        touchUp(screenX, screenY, pointer, button)

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false

    override fun scrolled(amountX: Float, amountY: Float): Boolean = false

    override fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Keys.LEFT, Keys.A -> keyLeft = true
            Keys.RIGHT, Keys.D -> keyRight = true
            Keys.SPACE -> {
                if (!keyJumpHeld) pressed.add("JUMP")
                keyJumpHeld = true
            }
            Keys.Z, Keys.SHIFT_LEFT -> pressed.add("ROLL")
            Keys.X, Keys.CONTROL_LEFT -> {
                if (!keyAttackHeld) pressed.add("ATTACK")
                keyAttackHeld = true
            }
            Keys.C -> pressed.add("Q")
            Keys.V -> pressed.add("E")
            else -> return false
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        when (keycode) {
            Keys.LEFT, Keys.A -> keyLeft = false
            Keys.RIGHT, Keys.D -> keyRight = false
            Keys.SPACE -> keyJumpHeld = false
            Keys.X, Keys.CONTROL_LEFT -> keyAttackHeld = false
            else -> return false
        }
        return true
    }

    private var attackWasHeld = false

    override fun poll(): InputSnapshot {
        var moveX = joyX
        if (keyLeft) moveX -= 1f
        if (keyRight) moveX += 1f
        moveX = moveX.coerceIn(-1f, 1f)

        val attackHeld = held.contains("ATTACK") || keyAttackHeld
        val snap = InputSnapshot(
            moveX = moveX,
            jumpHeld = held.contains("JUMP") || keyJumpHeld,
            jumpPressed = pressed.contains("JUMP"),
            rollPressed = pressed.contains("ROLL"),
            attackPressed = pressed.contains("ATTACK"),
            attackHeld = attackHeld,
            attackReleased = attackWasHeld && !attackHeld,
            skillQPressed = pressed.contains("Q"),
            skillEPressed = pressed.contains("E"),
        )
        attackWasHeld = attackHeld
        pressed.clear()
        return snap
    }

    /** 半透明占位绘制；P2 换图集。 */
    fun draw(shapes: ShapeRenderer) {
        shapes.color = Color(0.54f, 0.71f, 0.97f, 0.18f)
        for (b in buttons) {
            shapes.circle(b.x, b.y, b.r, 20)
        }
        if (held.contains("ATTACK")) {
            shapes.color = Color(0.54f, 0.71f, 0.97f, 0.40f)
            buttons.first { it.id == "ATTACK" }.let { shapes.circle(it.x, it.y, it.r, 20) }
        }
        // 摇杆
        if (joyPointer != -1) {
            shapes.color = Color(0.54f, 0.71f, 0.97f, 0.15f)
            shapes.circle(joyAnchorX, joyAnchorY, joystickRadius, 24)
            shapes.color = Color(0.79f, 0.80f, 0.87f, 0.45f)
            shapes.circle(joyAnchorX + joyX * joystickRadius, joyAnchorY, 8f, 16)
        }
    }
}

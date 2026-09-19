package com.mistbound.relics.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import kotlin.math.floor
import kotlin.math.min

/**
 * 480x270 虚拟分辨率 + 整数倍缩放 + letterbox（ENGINEERING 3）。
 * 自包含实现，不依赖 libGDX Viewport 子类行为。
 */
class PixelPerfectViewport(
    val worldWidth: Float,
    val worldHeight: Float,
) {
    val camera = OrthographicCamera()

    var left = 0
        private set
    var bottom = 0
        private set
    var pixelWidth = 0
        private set
    var pixelHeight = 0
        private set
    var scale = 1
        private set

    fun resize(screenWidth: Int, screenHeight: Int) {
        scale = maxOf(1, floor(min(screenWidth / worldWidth, screenHeight / worldHeight)).toInt())
        pixelWidth = (worldWidth * scale).toInt()
        pixelHeight = (worldHeight * scale).toInt()
        left = (screenWidth - pixelWidth) / 2
        bottom = (screenHeight - pixelHeight) / 2
        camera.setToOrtho(false, worldWidth, worldHeight)
        camera.update()
    }

    /** 每帧渲染前调用：把 GL viewport 限定到整数缩放区域。 */
    fun apply() {
        Gdx.gl.glViewport(left, bottom, pixelWidth, pixelHeight)
    }

    /** 屏幕坐标 -> 虚拟世界坐标（输入 y 为自上而下）。 */
    fun toWorldX(screenX: Float): Float = (screenX - left) / scale

    fun toWorldY(screenY: Float, screenHeight: Float): Float = ((screenHeight - screenY) - bottom) / scale

    fun contains(screenX: Float, screenHeight: Float): Boolean {
        val wx = toWorldX(screenX)
        val wy = toWorldY(screenY, screenHeight)
        return wx in 0f..worldWidth && wy in 0f..worldHeight
    }
}

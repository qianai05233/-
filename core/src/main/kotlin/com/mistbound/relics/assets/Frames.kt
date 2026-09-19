package com.mistbound.relics.assets

/**
 * frames.json 运行时清单（纯逻辑，无 Gdx 依赖；解析见 FramesManifest.parse(JsonValue)）。
 * 动画 = 帧名序列 + fps + loop + pivot(帧宽高归一化，脚底中心约定)。
 */
class Anim(
    val name: String,
    val frames: List<String>,
    val fps: Float,
    val loop: Boolean,
    val pivotX: Float,
    val pivotY: Float,
) {
    /** 动画一次完整播放时长（秒）。 */
    val duration: Float get() = frames.size / fps

    /** 采样 t 秒时的帧下标（循环动画取模，非循环钳制末帧）。 */
    fun frameIndex(t: Float): Int {
        val i = (t * fps).toInt()
        return if (loop) (if (frames.isEmpty()) 0 else i % frames.size) else i.coerceAtMost(frames.size - 1).coerceAtLeast(0)
    }
}

class FramesManifest(val anims: Map<String, Anim>) {
    fun anim(id: String): Anim =
        anims[id] ?: error("frames.json 缺少动画: $id (有: ${anims.keys.sorted().joinToString(",")})")

    val heroAnims get() = anims.keys.filter { it.startsWith("hero_") }
}

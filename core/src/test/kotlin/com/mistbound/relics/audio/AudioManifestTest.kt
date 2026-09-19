package com.mistbound.relics.audio

import com.badlogic.gdx.utils.JsonReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 音频清单校验：SfxId 枚举 ↔ gen_audio.py 产出的 audio_manifest.json 必须同步；
 * BGM 必须 5 层；时长/体积在合理范围（防静音/防膨胀）。
 */
class AudioManifestTest {

    private fun readText(vararg candidates: String): String {
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return f.readText()
        }
        error("找不到文件: ${candidates.joinToString()}")
    }

    private val json = JsonReader().parse(readText(
        "../core/src/main/resources/audio_manifest.json",
        "core/src/main/resources/audio_manifest.json",
        "src/main/resources/audio_manifest.json",
    ))

    private fun entries(name: String): List<Pair<String, Float>> {
        val arr = json.get(name)
        val out = ArrayList<Pair<String, Float>>()
        var c = arr.child
        while (c != null) {
            out.add(c.getString("file") to c.getFloat("duration"))
            c = c.next
        }
        return out
    }

    @Test
    fun `every sfx id has a generated ogg`() {
        val files = entries("sfx").map { it.first }.toSet()
        for (id in SfxId.entries) {
            assertTrue("${id.file}.ogg" in files, "缺少音效 ${id.file}.ogg")
        }
        assertEquals(SfxId.entries.size, files.size, "清单音效数与枚举不一致")
    }

    @Test
    fun `bgm has five layers with loopable duration`() {
        val music = entries("music")
        assertEquals(5, music.size, "BGM 必须 5 层")
        for ((file, dur) in music) {
            assertTrue(dur in 7f..12f, "$file 时长 $dur 不在循环窗口")
        }
        assertEquals(
            (1..5).map { "fog_${it - 1}.ogg" }.toSet(),
            music.map { it.first }.toSet(),
        )
    }

    @Test
    fun `sfx durations within sane bounds`() {
        for ((file, dur) in entries("sfx")) {
            assertTrue(dur in 0.02f..1.2f, "$file 时长 $dur 异常")
        }
    }
}

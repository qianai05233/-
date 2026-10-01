package com.mistbound.relics

import android.os.Bundle
import android.util.Log
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全局未捕获异常处理器：防止闪退无日志，方便 logcat 排查
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MistboundCrash", "Uncaught in ${thread.name}", throwable)
            // 打印 assets 列表，排查是否打包失败
            try {
                val list = assets.list("game")?.joinToString()
                Log.e("MistboundCrash", "assets/game listing: $list")
                val atlasList = assets.list("game/atlas")?.joinToString()
                Log.e("MistboundCrash", "assets/game/atlas listing: $atlasList")
            } catch (e: Exception) {
                Log.e("MistboundCrash", "failed to list assets", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useGyroscope = false
            useCompass = false
            // 修复：部分设备需要明确指定 GLES30 兼容，libGDX 1.13 默认使用 2.0
            useGL30 = false
        }

        try {
            Log.i("Mistbound", "Initializing MistboundGame, assets dir check...")
            // 启动前自检 assets 是否存在
            try {
                val hasFrames = assets.list("game")?.contains("frames.json") == true
                Log.i("Mistbound", "has game/frames.json in assets? $hasFrames")
            } catch (e: Exception) {
                Log.w("Mistbound", "assets check failed", e)
            }

            initialize(MistboundGame(), config)
        } catch (e: Exception) {
            Log.e("MistboundCrash", "Failed to initialize game", e)
            throw e
        }
    }
}

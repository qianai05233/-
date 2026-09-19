package com.mistbound.relics

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.mistbound.relics.render.GameScreen

class MistboundGame : Game() {
    override fun create() {
        Gdx.app.log("MistboundGame", "create() - setting GameScreen")
        try {
            setScreen(GameScreen())
            Gdx.app.log("MistboundGame", "GameScreen set successfully")
        } catch (e: Exception) {
            Gdx.app.error("MistboundGame", "Failed to create GameScreen - crash would happen here", e)
            throw e
        }
    }

    override fun dispose() {
        Gdx.app.log("MistboundGame", "dispose()")
        try {
            screen?.dispose()
        } catch (e: Exception) {
            Gdx.app.error("MistboundGame", "Error disposing screen", e)
        }
    }
}

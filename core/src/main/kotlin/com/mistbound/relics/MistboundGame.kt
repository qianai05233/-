package com.mistbound.relics

import com.badlogic.gdx.Game
import com.mistbound.relics.render.GameScreen

class MistboundGame : Game() {
    override fun create() {
        setScreen(GameScreen())
    }

    override fun dispose() {
        screen?.dispose()
    }
}

package com.microwavestudios.fortyfive.oven

import com.badlogic.gdx.Gdx

class Oven {

    fun bake(bakeTasks: List<BakeTask>) {
        bakeTasks.forEach { it.bake() }
        Gdx.app.exit()
    }

}

sealed interface BakeTask {

    fun bake()
}

package com.microwavestudios.fortyfive.screen.screenController

import com.microwavestudios.fortyfive.particle.ParticleSystem
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.ScreenController

class ParticleSystemScreenController(
    val system: ParticleSystem,
    private val screen: CustomScreen
) : ScreenController() {

    override fun onShow() {
        screen.addLateRenderTask { batch ->
            system.render(batch, screen)
        }
    }

    override fun update() {
        system.update()
    }
}

package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger

abstract class DebugMenuPage {

    abstract fun getText(screen: OnjScreen): String

}

class StandardDebugMenuPage : DebugMenuPage() {

    override fun getText(screen: OnjScreen) = """
        fps: ${Gdx.graphics.framesPerSecond}
        15s render lagSpike: ${screen.largestRenderTimeInLast15Sec()}ms
        15s avg. render time: ${screen.averageRenderTimeInLast15Sec()}ms
        active style managers: ${screen.styleManagerCount()}
        version: ${FortyFiveLogger.versionTag}
    """.trimIndent()
}

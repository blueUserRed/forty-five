package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.Resource
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger

abstract class DebugMenuPage(val name: String) {

    abstract fun getText(screen: OnjScreen): String

}

class StandardDebugMenuPage : DebugMenuPage("Performance infos") {

    override fun getText(screen: OnjScreen) = """
        fps: ${Gdx.graphics.framesPerSecond}
        15s render lagSpike: ${screen.largestRenderTimeInLast15Sec()}ms
        15s avg. render time: ${screen.averageRenderTimeInLast15Sec()}ms
        active style managers: ${screen.styleManagerCount()}
        version: ${FortyFiveLogger.versionTag}
    """.trimIndent()
}

class CardTextureDebugMenuPage : DebugMenuPage("Card Textures") {

    override fun getText(screen: OnjScreen): String {
        val statistics = FortyFive.cardTextureManager.statistics
        return """
            loaded textures: ${statistics.loadedTextures}
            texture usages: ${statistics.textureUsages}
            
            cached texture gets: ${statistics.cachedGets}
            texture draws: ${statistics.textureDraws}
            card pixmap loads: ${statistics.pixmapLoads}
            
            last card get: ${statistics.lastLoadedCard}
        """.trimIndent()
    }
}

class ResourceDebugMenuPage : DebugMenuPage("") {

    override fun getText(screen: OnjScreen): String {
        val unloaded = ResourceManager.resources
            .filter { it.state == Resource.ResourceState.NOT_LOADED && !it.startedLoading }
            .size
        val loading = ResourceManager.resources
            .filter { it.startedLoading }
            .size
        val loaded = ResourceManager.resources
            .filter { it.state == Resource.ResourceState.LOADED }
            .size
        return """
            unloaded: $unloaded
            loading: $loading
            loaded: $loaded
        """.trimIndent()
    }
}

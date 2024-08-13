package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.keyInput.Keycode
import com.fourinachamber.fortyfive.screen.Resource
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import kotlin.reflect.KProperty

abstract class DebugMenuPage(val name: String) {

    private val buttons: MutableList<DebugButton> = mutableListOf()

    fun update() {
        buttons.forEach {
            if (Gdx.input.isKeyJustPressed(it.key)) {
                it.set = !it.set
            }
        }
    }

    protected fun debugButton(
        name: String,
        key: Keycode,
        default: Boolean
    ): DebugButton = DebugButton(name, key, default).also { buttons.add(it) }

    abstract fun getText(screen: OnjScreen): String

    data class DebugButton(
        val name: String,
        val key: Keycode,
        var set: Boolean
    ) {
        override fun toString(): String = "[${if (set) "x" else " "}] $name <${Keys.toString(key)}>"

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = set

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            set = value
        }
    }

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

class ResourceDebugMenuPage : DebugMenuPage("Resources") {

    override fun getText(screen: OnjScreen): String {
        val unloaded = ResourceManager.resources
            .filter { it.state == Resource.ResourceState.NOT_LOADED && !it.startedLoading }
            .size
        val loading = ResourceManager.resources
            .filter { it.startedLoading && it.state != Resource.ResourceState.LOADED }
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

class MapDebugMenuPage : DebugMenuPage("Map") {

    val walkEverywhere = debugButton("walk everywhere", Keys.E, false)

    override fun getText(screen: OnjScreen): String = """
        $walkEverywhere
    """.trimIndent()
}

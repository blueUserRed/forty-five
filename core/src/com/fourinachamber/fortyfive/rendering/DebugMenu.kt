package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.Resource
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import kotlin.reflect.KProperty

class DebugMenu(val pages: List<DebugMenuPage>) {

    private var pageIndex: Int = 0
    var show: Boolean = false

    fun toggle() {
        show = !show
    }

    fun nextDebugPage() {
        pageIndex++
        if (pageIndex >= pages.size) pageIndex = 0
    }

    fun previousDebugPage() {
        pageIndex--
        if (pageIndex < 0) pageIndex = pages.size - 1
    }

    fun currentPage(): DebugMenuPage = pages[pageIndex]

    fun currentPageNumber(): Int = pageIndex + 1

    fun amountOfPages(): Int = pages.size

    fun update() {
        pages.forEach { it.update() }
    }

    inline fun <reified T : DebugMenuPage> findPage(): T? = pages.find { it is T } as T?

    fun newMenuWithPages(pageNames: List<String>): DebugMenu {
        val newPages = pageNames.map { name ->
            pages.find { it.name == name }
                ?: knownDebugMenuPages[name]?.invoke()
                ?: throw RuntimeException("unknown debug menu page $name")
        }
        val newMenu = DebugMenu(newPages)
        newMenu.show = show
        return newMenu
    }

    companion object {

        private val knownDebugMenuPages: MutableMap<String, () -> DebugMenuPage> = mutableMapOf()

        init {
            registerDebugMenuPage("Performance infos") { ScreenDebugMenuPage() }
            registerDebugMenuPage("Card Textures") { CardTextureDebugMenuPage() }
            registerDebugMenuPage("Resources") { ResourceDebugMenuPage() }
            registerDebugMenuPage("Map") { MapDebugMenuPage() }
        }

        fun registerDebugMenuPage(name: String, creator: () -> DebugMenuPage) {
            knownDebugMenuPages[name] = creator
        }

        fun fromNames(names: List<String>): DebugMenu {
            val menuPages = names.map { name ->
                knownDebugMenuPages[name]?.invoke()
                    ?: throw RuntimeException("unknown debug menu page $name")
            }
            return DebugMenu(menuPages)
        }

    }
}

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
        key: Int,
        default: Boolean
    ): DebugButton = DebugButton(name, key, default).also { buttons.add(it) }

    abstract fun getText(screen: OnjScreen): String

    data class DebugButton(
        val name: String,
        val key: Int,
        var set: Boolean
    ) {
        override fun toString(): String = "[${if (set) "x" else " "}] $name <${Keys.toString(key)}>"

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = set

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            set = value
        }
    }

}

class ScreenDebugMenuPage : DebugMenuPage("Performance infos") {

    val makeLaggy = debugButton("make laggy", Keys.L, false)

    override fun getText(screen: OnjScreen) = """
        fps: ${Gdx.graphics.framesPerSecond}
        version: ${FortyFive.logger.versionTag}
        15s render lagSpike: ${FortyFive.renderTimes.max()}ms
        15s avg. render time: ${FortyFive.renderTimes.average().toInt()}ms
        screen transition max lagSpike: ${FortyFive.screenTransitionTimes.max()}ms
        screen transition avg. lagSpike: ${FortyFive.screenTransitionTimes.average().toInt()}ms
        
        $makeLaggy
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

    val walkEverywhere = debugButton("walk everywhere", Keys.R, false)

    override fun getText(screen: OnjScreen): String = """
        $walkEverywhere
    """.trimIndent()
}

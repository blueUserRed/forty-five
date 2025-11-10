package com.microwavestudios.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.map.MapNode
import com.microwavestudios.fortyfive.resources.Resource
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.DebugActor
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
            registerDebugMenuPage("Basic infos") { BaseInfosDebugMenuPage() }
            registerDebugMenuPage("Screen/Input") { ScreenDebugMenuPage() }
            registerDebugMenuPage("Card Textures") { CardTextureDebugMenuPage() }
            registerDebugMenuPage("Resources") { ResourceDebugMenuPage() }
            registerDebugMenuPage("Map") { MapDebugMenuPage() }
            registerDebugMenuPage("Encounter Preview") { EncounterPreviewDebugMenuPage() }
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

class BaseInfosDebugMenuPage : DebugMenuPage("Basic infos") {

    override fun getText(screen: OnjScreen) = """
        fps: ${Gdx.graphics.framesPerSecond}
        version: ${FortyFive.logger.versionTag}
        15s render lagSpike: ${FortyFive.renderTimes.max()}ms
        15s avg. render time: ${FortyFive.renderTimes.average().toInt()}ms
    """.trimIndent()
}

class ScreenDebugMenuPage : DebugMenuPage("Screen/Input") {

    val makeLaggy = debugButton("make laggy", Keys.L, false)

    override fun getText(screen: OnjScreen): String = """
        focused with keyboard: ${
            screen.inputManager.keyboardFocused?.actor?.let {
                if (it is DebugActor) it.getDebugName() else it.toString()
            }
        }
        hovered: ${
            screen.inputManager.lastHovered?.let {
                if (it is DebugActor) it.getDebugName() else it.toString()
            }
        }
        currently dragged: ${
            screen.inputManager.currentlyDraggedActor?.actor?.let {
                if (it is DebugActor) it.getDebugName() else it.toString()
            }
        }
        
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
        val unloaded = FortyFive
            .resourceManager
            .resources
            .filter { it.state == Resource.ResourceState.NOT_LOADED && !it.startedLoading }
            .size
        val loading = FortyFive
            .resourceManager
            .resources
            .filter { it.startedLoading && it.state != Resource.ResourceState.LOADED }
            .size
        val loaded = FortyFive
            .resourceManager
            .resources
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

    var currentNode: MapNode? = null

    val walkEverywhere = debugButton("walk everywhere", Keys.R, false)

    override fun getText(screen: OnjScreen): String = """
        dist: ${currentNode?.distance}
        index: ${currentNode?.index}
        $walkEverywhere
    """.trimIndent()
}

class EncounterPreviewDebugMenuPage : DebugMenuPage("Encounter Preview") {

    var encounter: Encounter? = null

    override fun getText(screen: OnjScreen): String = encounter?.let { encounter ->
        """
            enemies: ${encounter.enemies.joinToString(separator = ", ")}
            modifier: ${encounter.encounterModifierNames.joinToString(separator = ", ")}
            major difficulty: ${encounter.majorDifficulty}
            minor difficulty: ${encounter.minorDifficulty}
            major difficulty (unadjusted): ${encounter.unadjustedMajorDifficulty}
            difficulty scaling contribution: ${encounter.difficultyScalingInfo}
        """.trimIndent()
    } ?: ""
}

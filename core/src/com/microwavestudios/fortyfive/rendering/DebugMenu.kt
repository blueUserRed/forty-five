package com.microwavestudios.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.map.MapNode
import com.microwavestudios.fortyfive.resources.Resource
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.DebugActor
import com.microwavestudios.fortyfive.screen.screens.WinRunScreen
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.findInstance
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
        pages.forEach { it.update(this) }
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
            registerDebugMenuPage("Encounter") { EncounterDebugMenuPage() }
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

    private val switches: MutableList<DebugSwitch> = mutableListOf()
    private val buttons: MutableList<DebugButton> = mutableListOf()

    fun update(menu: DebugMenu) {
        if (menu.currentPage() !== this) return
        switches.forEach {
            if (Gdx.input.isKeyJustPressed(it.key)) {
                it.set = !it.set
            }
        }
        buttons.forEach { button ->
            if (Gdx.input.isKeyJustPressed(button.key)) button.action()
        }
    }

    protected fun debugButton(
        name: String,
        key: Int,
        action: () -> Unit
    ): DebugButton = DebugButton(name, key, action).also { buttons.add(it) }

    protected fun debugSwitch(
        name: String,
        key: Int,
        default: Boolean
    ): DebugSwitch = DebugSwitch(name, key, default).also { switches.add(it) }

    abstract fun getText(screen: RenderableScreen): String

    data class DebugButton(
        val name: String,
        val key: Int,
        val action: () -> Unit
    ) {
        override fun toString(): String = "[-] $name <${Keys.toString(key)}>"
    }

    data class DebugSwitch(
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

    override fun getText(screen: RenderableScreen) = """
        fps: ${Gdx.graphics.framesPerSecond}
        version: ${FortyFive.logger.versionTag}
        15s render lagSpike: ${FortyFive.renderTimes.max()}ms
        15s avg. render time: ${FortyFive.renderTimes.average().toInt()}ms
    """.trimIndent()
}

class ScreenDebugMenuPage : DebugMenuPage("Screen/Input") {

    val makeLaggy = debugSwitch("make laggy", Keys.L, false)

    override fun getText(screen: RenderableScreen): String = """
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

    override fun getText(screen: RenderableScreen): String {
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

    override fun getText(screen: RenderableScreen): String {
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

    val walkEverywhere = debugSwitch("walk everywhere", Keys.R, false)

    val countSteps = debugSwitch("count steps", Keys.M, true)

    val completeRun = debugButton("complete run", Keys.Q) {
        val profile = FortyFive.profileManager.currentProfile ?: return@debugButton
        if (profile.activeRun == null) return@debugButton
        FortyFive.screenManager.ensureNextScreen(WinRunScreen)
        FortyFive.screenManager.screenFinished()
    }

    override fun getText(screen: RenderableScreen): String = """
        dist: ${currentNode?.distance}
        index: ${currentNode?.index}
        $completeRun
        $walkEverywhere
        $countSteps
    """.trimIndent()
}

class EncounterPreviewDebugMenuPage : DebugMenuPage("Encounter Preview") {

    var encounter: Encounter? = null

    override fun getText(screen: RenderableScreen): String = encounter?.let { encounter ->
        """
            enemies: ${encounter.enemiesGroups.joinToString(separator = ", ")}
            modifier: ${encounter.encounterModifierNames.joinToString(separator = ", ")}
            major difficulty: ${encounter.majorDifficulty}
            minor difficulty: ${encounter.minorDifficulty}
            major difficulty (unadjusted): ${encounter.unadjustedMajorDifficulty}
            difficulty scaling contribution: ${encounter.difficultyScalingInfo}
        """.trimIndent()
    } ?: ""
}

class EncounterDebugMenuPage : DebugMenuPage("Encounter") {

    val defeatEnemies = debugButton("defeat Enemies", Keys.U) {
        val screen = FortyFive.currentScreen ?: return@debugButton
        val controller = screen.screenControllers.findInstance<GameController>() ?: return@debugButton
        controller.appendMainTimeline(Timeline.timeline { later {
            controller.activeEnemies.forEach { enemy ->
                includeLater({ enemy.damage(enemy.currentCover + enemy.currentHealth) })
            }
        } })
    }

    val giveReserves = debugButton("give reserves", Keys.I) {
        val screen = FortyFive.currentScreen ?: return@debugButton
        val controller = screen.screenControllers.findInstance<GameController>() ?: return@debugButton
        controller.appendMainTimeline(Timeline.timeline {
            action { controller.gainReserves(4) }
        })
    }

    val drawCards = debugButton("draw card", Keys.O) {
        val screen = FortyFive.currentScreen ?: return@debugButton
        val controller = screen.screenControllers.findInstance<GameController>() ?: return@debugButton
        controller.appendMainTimeline(controller.drawCardsTimeline(2))
    }

    override fun getText(screen: RenderableScreen): String = """
        $giveReserves
        $drawCards
        $defeatEnemies
    """.trimIndent()

}

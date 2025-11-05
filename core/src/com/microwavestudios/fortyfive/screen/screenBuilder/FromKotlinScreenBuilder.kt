package com.microwavestudios.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.microwavestudios.fortyfive.rendering.DebugMenu
import com.microwavestudios.fortyfive.screen.OnjScreen

class FromKotlinScreenBuilder(val creator: ScreenCreator) : ScreenBuilder {

    override val name: String = creator.name

    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    private val commonDebugMenuPages: List<String> = listOf("Basic infos", "Screen/Input", "Card Textures", "Resources")

    override fun build(controllerContext: Any?, previousScreen: OnjScreen?): OnjScreen {
        val screen = OnjScreen(
            viewport = creator.viewport,
            batch = SpriteBatch(),
            controllerContext = controllerContext,
            earlyRenderTasks = listOf({ creator.update() }),
            lateRenderTasks = listOf(),
            namedActors = namedActors,
            transitions = creator.transitions,
            screenBuilder = this,
            music = null,
            playAmbientSounds = creator.playAmbientSounds
        )
        val debugMenuPages = commonDebugMenuPages + creator.debugMenuPages()
        val previousMenu = previousScreen?.debugMenu
        val debugMenu = if (previousMenu == null) {
            DebugMenu.fromNames(debugMenuPages)
        } else {
            previousMenu.newMenuWithPages(debugMenuPages)
        }
        screen.debugMenu = debugMenu
        creator.start(screen, controllerContext)
        val controllers = creator.getScreenControllers()
        controllers.forEach { it.preInit(controllerContext) }
        val root = creator.getRoot()
        screen.stage.root = root
        screen.background = creator.background
        namedActors.putAll(creator.namedActors)
        controllers.forEach { screen.addScreenController(it) }
        return screen
    }

}

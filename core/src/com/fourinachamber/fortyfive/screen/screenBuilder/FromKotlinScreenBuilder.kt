package com.fourinachamber.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.rendering.DebugMenu
import com.fourinachamber.fortyfive.screen.general.OnjScreen

class FromKotlinScreenBuilder(val creator: ScreenCreator) : ScreenBuilder {

    override val name: String = creator.name

    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    private val commonDebugMenuPages: List<String> = listOf("Performance infos", "Card Textures", "Resources")

    override fun build(controllerContext: Any?, previousScreen: OnjScreen?): OnjScreen {
        val screen = OnjScreen(
            viewport = creator.viewport,
            batch = SpriteBatch(),
            controllerContext = controllerContext,
            earlyRenderTasks = listOf({ creator.update() }),
            lateRenderTasks = listOf(),
            namedActors = namedActors,
            transitionAwayTimes = creator.transitionAwayTimes,
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
        val root = creator.getRoot()
        screen.stage.root = root
        screen.background = creator.background
        namedActors.putAll(creator.namedActors)
        creator.getScreenControllers().forEach { screen.addScreenController(it) }
        return screen
    }

}

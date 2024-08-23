package com.fourinachamber.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import dev.lyze.flexbox.FlexBox

class FromKotlinScreenBuilder(val creator: ScreenCreator) : ScreenBuilder {

    override val name: String = creator.name

    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    override fun build(controllerContext: Any?): OnjScreen {
        val screen = OnjScreen(
            viewport = creator.viewport,
            batch = SpriteBatch(),
            controllerContext = controllerContext,
            earlyRenderTasks = listOf(),
            lateRenderTasks = listOf(),
            styleManagers = listOf(),
            namedActors = namedActors,
            printFrameRate = false,
            transitionAwayTimes = creator.transitionAwayTimes,
            screenBuilder = this,
            music = null,
            playAmbientSounds = creator.playAmbientSounds
        )
        creator.start(screen)
        val root = creator.getRoot()
        screen.stage.root = root
        screen.background = creator.background
        return screen
    }

    override fun generateFromTemplate(
        name: String,
        data: Map<String, Any?>,
        parent: Group?,
        screen: OnjScreen
    ): Actor? {
        TODO("Not yet implemented")
    }

    override fun addDataToWidgetFromTemplate(
        name: String,
        data: Map<String, Any?>,
        parent: FlexBox?,
        screen: OnjScreen,
        actor: Actor,
        removeOldData: Boolean
    ) {
        TODO("Not yet implemented")
    }
}

package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility


class ScreenManager(
    private var baseScreen: Pair<() -> ScreenBuilder, Any?>
) {

    private val chain: ScreenChain = ScreenChain(listOf())

    constructor(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) : this(
        { FromKotlinScreenBuilder(creatorFromClass(creatorCompanion.creatorClass)) } to context
    )

    fun newBaseScreen(screenBuilder: () -> ScreenBuilder, context: Any? = null) {
        baseScreen = screenBuilder to context
    }

    fun newBaseScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        baseScreen = {
            val creator = creatorFromClass(creatorCompanion.creatorClass)
            FromKotlinScreenBuilder(creator)
        } to context
    }

    fun screenFinished() {
        val next = chain.next() ?: (baseScreen.first() to baseScreen.second)
        changeToScreen(next.first, next.second)
    }

    fun appendScreen(screenBuilder: ScreenBuilder, context: Any? = null) {
        chain.append(screenBuilder, context)
    }

    fun appendScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        val creator = creatorFromClass(creatorCompanion.creatorClass)
        chain.append(FromKotlinScreenBuilder(creator), context)
    }

    fun ensureNextScreen(screenBuilder: ScreenBuilder, context: Any? = null) {
        chain.pushScreenToFront(screenBuilder, context)
    }

    fun ensureNextScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        val creator = creatorFromClass(creatorCompanion.creatorClass)
        chain.pushScreenToFront(FromKotlinScreenBuilder(creator), context)
    }

    private var inScreenTransition: Boolean = false
    private var nextScreen: OnjScreen? = null
    private var currentScreen: OnjScreen? = null

    private fun changeToScreen(screenBuilder: ScreenBuilder, context: Any?) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        if (currentScreen?.transitionAwayTimes != null) currentScreen.transitionAway()
        val screen = screenBuilder.build(context, currentScreen)
        nextScreen = screen

        fun onScreenChange() {
            FortyFive.logger.title("changing screen to ${screenBuilder.name}")
            currentScreen?.dispose()
            MapManager.invalidateCachedAssets()
            this.currentScreen = screen
            FortyFive.currentScreen = screen
            nextScreen = null
            FortyFive.useRenderPipeline(RenderPipeline(screen, screen))
            FortyFive.setScreen(screen)
            inScreenTransition = false
//            FortyFive.inMs(100) {
//                val lagSpike = renderTimes.max()
//                screenTransitionTimes[(screenTransitionCount % screenTransitionTimes.size).toInt()] = lagSpike
//                screenTransitionCount++
//            }
        }

        val transitionAwayTime = currentScreen?.transitionAwayTimes?.let {
            it[screenBuilder.name] ?: it["*"]
        } ?: 0
        if (currentScreen == null) {
            onScreenChange()
        } else currentScreen.afterMs(transitionAwayTime) {
            onScreenChange()
        }
    }

    private class ScreenChain(screens: List<Pair<ScreenBuilder, Any?>>) {

        private val screens: MutableList<Pair<ScreenBuilder, Any?>> = screens.toMutableList()

        fun next(): Pair<ScreenBuilder, Any?>? = screens.removeFirstOrNull()

        fun append(builder: ScreenBuilder, context: Any? = null) {
            screens.add(builder to context)
        }

        fun pushScreenToFront(builder: ScreenBuilder, context: Any? = null) {
            screens.add(0, builder to context)
        }

    }

    interface ScreenCreatorCompanion {
        val creatorClass: KClass<out ScreenCreator>
    }

    companion object {

        private fun creatorFromClass(creatorClass: KClass<out ScreenCreator>): ScreenCreator {
            val constructor = creatorClass.constructors.find { constructor ->
                constructor.parameters.isEmpty() && constructor.visibility == KVisibility.PUBLIC
            }
            constructor ?: throw RuntimeException("ScreenCreator must have at least one constructor with no parameters")
            val creator = constructor.call() as ScreenCreator
            return creator
        }
    }
}

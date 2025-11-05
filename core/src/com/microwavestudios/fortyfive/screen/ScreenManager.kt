package com.microwavestudios.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenBuilder
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility


class ScreenManager(
    private var baseScreen: Pair<() -> ScreenBuilder, Any?>
) {

    private val chain: ScreenChain = ScreenChain(listOf())

    private val timeline: Timeline = Timeline().also { it.startTimeline() }

    private var overrideTransition: ScreenTransition? = null

    constructor(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) : this(
        { FromKotlinScreenBuilder(creatorFromClass(creatorCompanion.creatorClass)) } to context
    )

    fun overrideNextTransition(transition: ScreenTransition) {
        overrideTransition = transition
    }

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

    fun update() {
        timeline.updateTimeline()
    }

    private var inScreenTransition: Boolean = false
    private var nextScreen: OnjScreen? = null
    private var currentScreen: OnjScreen? = null

    private fun changeToScreen(screenBuilder: ScreenBuilder, context: Any?) = Gdx.app.postRunnable {
        if (inScreenTransition) return@postRunnable
        inScreenTransition = true
        val currentScreen = currentScreen
        val screen = screenBuilder.build(context, currentScreen)
        nextScreen = screen

        val transition =
            overrideTransition
            ?: currentScreen?.transitions[screenBuilder.name]
            ?: currentScreen?.transitions["*"]

        overrideTransition = null

        val timeline = Timeline.timeline {
            action {
                currentScreen?.transitionAway()
            }
            transition?.transitionAway?.let {
                include(it())
            }
            action {
                currentScreen?.dispose()
                this@ScreenManager.currentScreen = screen
                FortyFive.currentScreen = screen
                nextScreen = null
                FortyFive.useRenderPipeline(RenderPipeline(screen, screen))
                FortyFive.setScreen(screen)
            }
            transition?.transitionTo?.let {
                later { include(it()) }
            }
            action {
                val profile = FortyFive.profileManager.currentProfile
                profile?.currentMapSaver?.currentMap?.invalidateCachedAssets()
                profile?.write()
                profile?.writeMaps()
                screen.active()
                inScreenTransition = false
            }
        }
        this.timeline.appendAction(timeline.asAction())
    }

//    private fun changeToScreen(screenBuilder: ScreenBuilder, context: Any?) = Gdx.app.postRunnable {
//        if (inScreenTransition) return@postRunnable
//        inScreenTransition = true
//        val currentScreen = currentScreen
//        if (currentScreen?.transitionAwayTimes != null) currentScreen.transitionAway()
//        val screen = screenBuilder.build(context, currentScreen)
//        nextScreen = screen
//
//        fun onScreenChange() {
//            FortyFive.logger.title("changing screen to ${screenBuilder.name}")
//            currentScreen?.dispose()
//            this.currentScreen = screen
//            FortyFive.currentScreen = screen
//            nextScreen = null
//            FortyFive.useRenderPipeline(RenderPipeline(screen, screen))
//            FortyFive.setScreen(screen)
//            inScreenTransition = false
//            val profile = FortyFive.profileManager.currentProfile
//            profile?.currentMapSaver?.currentMap?.invalidateCachedAssets()
//            profile?.write()
//            profile?.writeMaps()
//        }
//
//        val transitionAwayTime = currentScreen?.transitionAwayTimes?.let {
//            it[screenBuilder.name] ?: it["*"]
//        } ?: 0
//        if (currentScreen == null) {
//            onScreenChange()
//        } else currentScreen.afterMs(transitionAwayTime) {
//            onScreenChange()
//        }
//    }

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

    data class ScreenTransition(
        val transitionAway: (() -> Timeline)?,
        val transitionTo: (() -> Timeline)?,
    )

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

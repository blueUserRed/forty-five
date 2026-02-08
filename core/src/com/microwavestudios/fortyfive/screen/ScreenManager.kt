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

/**
 * manages what screen is active and which screen comes next
 *
 * The ScreenManager maintains a list of screens that should be shown after the
 * current screen finishes. Screens can be added to the list via the [appendScreen]
 * and [ensureNextScreen] functions. If [screenFinished] is then called, the
 * ScreenManager will transition to the next screen in the list. If the list
 * is empty, the current base screen will be chosen instead. The base screen
 * is typically either the title or the map screen.
 */
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
        FortyFive.logger.debug(logTag, "new base screen")
        baseScreen = screenBuilder to context
    }

    fun newBaseScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        val className = creatorCompanion.creatorClass.simpleName
        FortyFive.logger.debug(logTag, "new base screen: $className")
        baseScreen = {
            val creator = creatorFromClass(creatorCompanion.creatorClass)
            FromKotlinScreenBuilder(creator)
        } to context
    }

    /**
     * starts the screen transition
     */
    fun screenFinished() {
        val next = chain.next() ?: (baseScreen.first() to baseScreen.second)
        FortyFive.logger.debug(logTag, "screenFinished called; new screen: ${next.first.name}")
        changeToScreen(next.first, next.second)
    }

    /**
     * appends a screen to the end of the list of screens (see [ScreenManager])
     */
    fun appendScreen(screenBuilder: ScreenBuilder, context: Any? = null) {
        FortyFive.logger.debug(logTag, "screen appended: ${screenBuilder.name}")
        chain.append(screenBuilder, context)
    }

    /**
     * appends a screen to the end of the list of screens (see [ScreenManager])
     */
    fun appendScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        FortyFive.logger.debug(logTag, "screen appended: ${creatorCompanion.creatorClass.simpleName}")
        val creator = creatorFromClass(creatorCompanion.creatorClass)
        chain.append(FromKotlinScreenBuilder(creator), context)
    }

    /**
     * add a screen to the start of the list of screens (see [ScreenManager])
     */
    fun ensureNextScreen(screenBuilder: ScreenBuilder, context: Any? = null) {
        FortyFive.logger.debug(logTag, "ensure next screen: ${screenBuilder.name}")
        chain.pushScreenToFront(screenBuilder, context)
    }

    /**
     * add a screen to the start of the list of screens (see [ScreenManager])
     */
    fun ensureNextScreen(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        FortyFive.logger.debug(logTag, "ensure next screen: ${creatorCompanion.creatorClass.simpleName}")
        val creator = creatorFromClass(creatorCompanion.creatorClass)
        chain.pushScreenToFront(FromKotlinScreenBuilder(creator), context)
    }

    fun update() {
        timeline.updateTimeline()
    }

    private var inScreenTransition: Boolean = false
    private var nextScreen: CustomScreen? = null
    private var currentScreen: CustomScreen? = null

    private fun changeToScreen(screenBuilder: ScreenBuilder, context: Any?) = Gdx.app.postRunnable {
        if (inScreenTransition) {
            FortyFive.logger.warn(logTag, "didn't perform screen change because there is an ongoing screen transition")
            return@postRunnable
        }
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

        private const val logTag = "ScreenManager"

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

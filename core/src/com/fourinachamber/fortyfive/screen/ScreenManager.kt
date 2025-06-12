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

    private val chains: MutableList<ScreenChain> = mutableListOf()

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
        var next: Pair<ScreenBuilder, Any?>? = null
        while (next == null && chains.isNotEmpty()) {
            val chain = chains.first()
            next = chain.next()
            if (next == null) chains.removeFirst()
        }
        if (next == null) next = baseScreen.first() to baseScreen.second
        changeToScreen(next.first, next.second)
    }

    fun addChain(chain: ScreenChain) {
        chains.add(chain)
    }

    fun addChainAndTransition(chain: ScreenChain) {
        addChain(chain)
        screenFinished()
    }

    fun transitionImmediate(screenBuilder: ScreenBuilder, context: Any? = null) {
        addChainAndTransition(ScreenChain(listOf(screenBuilder to context)))
    }

    fun transitionImmediate(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
        val creator = creatorFromClass(creatorCompanion.creatorClass)
        val builder = FromKotlinScreenBuilder(creator)
        transitionImmediate(builder, context)
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

    class ScreenChain(screens: List<Pair<ScreenBuilder, Any?>>) {

        private val screens: MutableList<Pair<ScreenBuilder, Any?>> = screens.toMutableList()

        fun next(): Pair<ScreenBuilder, Any?>? = screens.removeFirstOrNull()

        fun append(builder: ScreenBuilder, context: Any? = null) {
            screens.add(builder to context)
        }

        fun append(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
            val creator = creatorFromClass(creatorCompanion.creatorClass)
            append(FromKotlinScreenBuilder(creator), context)
        }

        fun pushScreenToFront(builder: ScreenBuilder, context: Any? = null) {
            screens.add(0, builder to context)
        }

        fun pushScreenToFront(creatorCompanion: ScreenCreatorCompanion, context: Any? = null) {
            val creator = creatorFromClass(creatorCompanion.creatorClass)
            pushScreenToFront(FromKotlinScreenBuilder(creator), context)
        }
    }

    interface ScreenCreatorCompanion {
        val creatorClass: KClass<out ScreenCreator>
    }

    companion object {

        // can't be a secondary constructor because it would have the same function signature as the primary constructor
        fun screenChain(vararg screens: Pair<ScreenCreatorCompanion, Any?>) = ScreenChain(
            screens.map { (companion, context) ->
                FromKotlinScreenBuilder(creatorFromClass(companion.creatorClass)) to context
            }
        )

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

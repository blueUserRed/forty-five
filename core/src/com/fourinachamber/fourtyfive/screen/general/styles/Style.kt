package com.fourinachamber.fourtyfive.screen.general.styles

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
//import com.fourinachamber.fourtyfive.onjNamespaces.OnjStyleProperty
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.YogaNode
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class Style(
    val properties: List<StyleProperty<*>>
) {

//    companion object {
//
//        fun readStyle(obj: OnjObject): Pair<String, Style> {
//            val properties = obj
//                .get<OnjArray>("properties")
//                .value
//                .map {
//                    it as OnjStyleProperty
//                    it.value
//                }
//            return obj.get<String>("name") to Style(properties)
//        }
//
//        fun readFromFile(path: String): Map<String, Style> {
//            val obj = OnjParser.parseFile(Gdx.files.internal(path).file())
//            schema.assertMatches(obj)
//            obj as OnjObject
//
//            return obj
//                .get<OnjArray>("styles")
//                .value
//                .associate { style ->
//                    style as OnjObject
//                    readStyle(style)
//                }
//        }
//
//        private val schema: OnjSchema by lazy {
//            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/style.onjschema").file())
//        }
//
//    }
}

class StyleTarget(
    val node: YogaNode,
    val actor: Actor,
    private val styles: List<Style>,
    private val directProperties: List<StyleProperty<*>>
) {

    private lateinit var screen: OnjScreen

    private val animations: MutableList<StyleAnimation> = mutableListOf()

    private val activeUnconditionalProperties: MutableList<StyleProperty<*>> = mutableListOf()
    private val activeConditionalProperties: MutableList<StyleProperty<*>> = mutableListOf()

    fun addAnimation(animation: StyleAnimation) {
        animations.add(animation)
        animation.startTimeNanos = TimeUtils.nanoTime()
    }

    fun update() {
        val iterator = animations.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            val timeSinceStart = TimeUtils.timeSinceNanos(animation.startTimeNanos)
            var rawPercent = ((timeSinceStart.toDouble() / 1_000_000.0) / animation.duration.toDouble()).toFloat()
            if (rawPercent > 1f) {
                rawPercent = 1f
                iterator.remove()
            }
            val percent = animation.interpolation.apply(rawPercent)
            println("$rawPercent $percent")
            animation.updateCallback(percent, actor, node)
        }
        for (style in styles) {
            for (property in style.properties) updateProperty(screen, property)
        }
        for (property in directProperties) updateProperty(screen, property)
    }

    fun init(screen: OnjScreen) {
        this.screen = screen
        for (style in styles) {
            for (property in style.properties) initProperty(screen, property)
        }
        for (property in directProperties) initProperty(screen, property)
    }

    private fun reapplyProperties(screen: OnjScreen) {
        for (property in activeUnconditionalProperties) property.applyToOrError(node, actor, screen, this)
        for (property in activeConditionalProperties) property.applyToOrError(node, actor, screen, this)
    }

    private fun updateProperty(screen: OnjScreen, property: StyleProperty<*>) {
        val condition = property.condition ?: return
        val state = condition.update() ?: return
        if (state) {
            activeConditionalProperties.add(property)
            reapplyProperties(screen)
        } else {
            activeConditionalProperties.remove(property)
            reapplyProperties(screen)
        }
    }

    private fun initProperty(screen: OnjScreen, property: StyleProperty<*>) {
        val condition = property.condition
        if (condition != null) {
            condition.init(screen, actor)
            return
        }
        property.applyToOrError(node, actor, screen, this)
        activeUnconditionalProperties.add(property)
    }

}

data class StyleAnimation(
    val duration: Int,
    var startTimeNanos: Long,
    val interpolation: Interpolation,
    val updateCallback: (percent: Float, actor: Actor, node: YogaNode) -> Unit
)

sealed class StyleCondition {

    private var lastState: Boolean = true

    protected lateinit var screen: OnjScreen
    protected lateinit var actor: Actor

    class Hover(val actorRef: StyleActorReference) : StyleCondition() {

        private var isHoveredOver: Boolean = false

        override fun init(screen: OnjScreen, actor: Actor) {
            super.init(screen, actor)
            val target = actorRef.get(actor, screen)
            target.actor.onEnter { isHoveredOver = true }
            target.actor.onExit { isHoveredOver = false }
        }

        override fun evaluate(): Boolean = isHoveredOver
    }

    class ScreenState(val state: String) : StyleCondition() {

        override fun evaluate(): Boolean = state in screen.screenState
    }

    abstract fun evaluate(): Boolean

    fun update(): Boolean? {
        val curState = evaluate()
        if (lastState == curState) return null
        lastState = curState
        return curState
    }

    open fun init(screen: OnjScreen, actor: Actor) {
        this.screen = screen
        this.actor = actor
    }

}

sealed class StyleActorReference {

//    object Self : StyleActorReference() {
//
//        override fun get(actor: Actor, screen: OnjScreen): StyleTarget {
//            return screen.styleManagers.find { it.actor === actor }
//                ?: throw RuntimeException("could not find style target of self ref") // this should never happen
//        }
//    }

    abstract fun get(actor: Actor, screen: OnjScreen): StyleTarget

}

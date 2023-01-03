package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.utils.epsilonEquals
import io.github.orioncraftmc.meditate.YogaNode
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class Style(
    val properties: List<StyleProperty>
) {

    companion object {

        fun readStyle(obj: OnjObject): Pair<String, Style> {
            val properties = obj
                .get<OnjArray>("properties")
                .value
                .map {
                    it as OnjStyleProperty
                    it.value
                }
            return obj.get<String>("name") to Style(properties)
        }

        fun readFromFile(path: String): Map<String, Style> {
            val obj = OnjParser.parseFile(Gdx.files.internal(path).file())
            schema.assertMatches(obj)
            obj as OnjObject

            return obj
                .get<OnjArray>("styles")
                .value
                .associate { style ->
                    style as OnjObject
                    readStyle(style)
                }
        }

        private val schema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/style.onjschema").file())
        }

    }
}

class StyleTarget(
    private val node: YogaNode,
    private val actor: Actor,
    private val styles: List<Style>,
    private val directProperties: List<StyleProperty>
) {

    private val animations: MutableList<StyleAnimation> = mutableListOf()

    fun addAnimation(animation: StyleAnimation) {
        animations.add(animation)
        animation.startTime = TimeUtils.millis()
    }

    fun update() {
        val iterator = animations.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            val timeSinceStart = TimeUtils.millis() - animation.startTime
            val rawPercent = timeSinceStart.toFloat() / animation.duration.toFloat()
            val percent = animation.interpolation.apply(rawPercent)
            animation.updateCallback(percent, actor, node)
            if (rawPercent >= 1f) iterator.remove()
        }
    }

    fun init(screen: StyleableOnjScreen) {
        for (style in styles) {
            for (property in style.properties) initProperty(screen, property)
        }
        for (property in directProperties) initProperty(screen, property)
    }

    private fun reapplyPropertiesWithoutConditions(screen: StyleableOnjScreen) {
        styles
            .flatMap { it.properties }
            .filter { it.condition == null }
            .forEach { it.applyTo(node, actor, screen, this) }

        directProperties
            .filter { it.condition == null }
            .forEach { it.applyTo(node, actor, screen, this) }
    }

    private fun initProperty(screen: StyleableOnjScreen, property: StyleProperty) {
        val condition = property.condition
        if (condition == null) {
            property.applyTo(node, actor, screen, this)
            return
        }

        when (condition) {

            is StyleCondition.Hover -> {
                val (actor, node) = condition.actor.get(node, actor, screen)
                actor.onEnter { property.applyTo(node, actor, screen, this@StyleTarget) }
                actor.onExit { reapplyPropertiesWithoutConditions(screen) }
            }

        }
    }

}

data class StyleAnimation(
    val duration: Int,
    var startTime: Long,
    val interpolation: Interpolation,
    val updateCallback: (percent: Float, actor: Actor, node: YogaNode) -> Unit
)

sealed class StyleCondition {

    class Hover(val actor: StyleActorReference) : StyleCondition()

}

sealed class StyleActorReference {

    class Self() : StyleActorReference() {

        override fun get(node: YogaNode, actor: Actor, screen: StyleableOnjScreen): Pair<Actor, YogaNode> {
            return actor to node
        }
    }

    abstract fun get(node: YogaNode, actor: Actor, screen: StyleableOnjScreen): Pair<Actor, YogaNode>

}

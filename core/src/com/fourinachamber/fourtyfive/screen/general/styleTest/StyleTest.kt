package com.fourinachamber.fourtyfive.screen.general.styleTest

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fourtyfive.screen.general.HoverStateActor
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.YogaNode
import kotlin.reflect.KClass

interface StyledActor : HoverStateActor {

    var styleManager: StyleManager

    fun initStyles(node: YogaNode, screen: OnjScreen)

}

class StyleManager(val actor: Actor, val node: YogaNode) {

    private val _styleProperties: MutableList<StyleProperty<*, *>> = mutableListOf()

    val styleProperties: List<StyleProperty<*, *>>
        get() = _styleProperties

    fun update() {
        _styleProperties.forEach { it.update() }
    }

    fun addStyleProperty(property: StyleProperty<*, *>) {
        _styleProperties.add(property)
    }

    fun addInstruction(propertyName: String, instruction: StyleInstruction<Any>, dataTypeClass: KClass<*>) {
        val property = styleProperties
            .find { it.name == propertyName && dataTypeClass == it.dataTypeClass }
            ?: throw RuntimeException("no style property $propertyName with type ${dataTypeClass.simpleName}")
        @Suppress("UNCHECKED_CAST") // this should be safe (I hope)
        property as StyleProperty<*, Any>
        property.addInstruction(instruction)
    }

}

abstract class StyleProperty<Target, DataType>(
    val name: String,
    val target: Target,
    val node: YogaNode,
    val default: DataType,
    val dataTypeClass: KClass<DataType>,
    val invalidate: Boolean,
    val invalidateHierarchy: Boolean,
    val screen: OnjScreen
) where Target : Actor, Target : StyledActor, DataType : Any {

    private val instructions: MutableList<StyleInstruction<DataType>> = mutableListOf()

    private var currentInstruction: StyleInstruction<DataType>? = null

    fun update() {
        val current = get()
        val top = instructions
            .filter { it.condition.check(target, screen) }
            .maxByOrNull { it.priority }
        currentInstruction = top
        top ?: return
        val value = top.value
        if (current == value) return
        set(value)
        if (invalidate && target is Layout) target.invalidate()
        if (invalidateHierarchy && target is Layout) target.invalidateHierarchy()
    }

    fun addInstruction(instruction: StyleInstruction<DataType>) {
        instructions.add(instruction)
    }

    abstract fun set(data: DataType)
    abstract fun get(): DataType

}

open class StyleInstruction<DataType>(
    val data: DataType,
    val priority: Int,
    val condition: StyleCondition
) {

    open val value: DataType
        get() = data

}

sealed class StyleCondition {

    object Always : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor = true
    }

    object IsHoveredOver : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            actor.isHoveredOver
    }

    class ScreenState(val state: String) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            state in screen.screenState
    }

    abstract fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor

}

package com.fourinachamber.fourtyfive.screen.general.styleTest

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.HoverStateActor
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
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

    companion object {

        const val logTag = "style"

        @Suppress("UNCHECKED_CAST") // the `as T` casts are safe
        fun <T> lerpStyleData(type: KClass<*>, from: T, to: T, percent: Float): T? {
            return when (from) {

                is Float -> (from + ((to as Float) - from) * percent) as T

                is YogaValue -> {
                    to as YogaValue
                    if (to.unit != from.unit || to.unit in arrayOf(YogaUnit.AUTO, YogaUnit.UNDEFINED)) {
                        FourtyFiveLogger.medium(logTag, "attempted to animate a property of type YogaValue, " +
                                "but the units used are either mixed or set to auto or undefined")
                        return null
                    }
                    YogaValue(from.value + (to.value - from.value) * percent, to.unit) as T
                }

                else -> {
                    FourtyFiveLogger.medium(logTag, "attempted to animate property of type ${type.simpleName}, " +
                            "which currently cannot be interpolated")
                    null
                }
            }
        }
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
        if (currentInstruction != top) {
            currentInstruction?.onControlLost()
            currentInstruction = top
            currentInstruction?.onControlGained(current)
        }
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
    val condition: StyleCondition,
    val dataTypeClass: KClass<*>
) {

    open val value: DataType
        get() = data


    open fun onControlGained(valueBefore: DataType) { }

    open fun onControlLost() { }

}

class AnimatedStyleInstruction<DataType>(
    data: DataType,
    priority: Int,
    condition: StyleCondition,
    dataTypeClass: KClass<*>,
    val duration: Int,
    val interpolation: Interpolation
) : StyleInstruction<DataType>(data, priority, condition, dataTypeClass) {

    private var startValue: DataType? = null
    private var startTime: Long = 0L

    override val value: DataType
        get() {
            if (startValue == null) return data
            if (TimeUtils.millis() >= startTime + duration) {
                startValue = null
                return data
            }
            val percent = ((TimeUtils.millis().toDouble() - startTime.toDouble()) / duration.toDouble()).toFloat()
            return StyleManager.lerpStyleData(
                dataTypeClass,
                startValue!!,
                data,
                percent
            ) ?: run {
                startValue = null
                startTime = 0L
                data
            }
        }

    override fun onControlGained(valueBefore: DataType) {
        startValue = valueBefore
        startTime = TimeUtils.millis()
    }

    override fun onControlLost() {
        startValue = null
        startTime = 0
    }
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

    class Or(val first: StyleCondition, val second: StyleCondition) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            first.check(actor, screen) || second.check(actor, screen)
    }

    class And(val first: StyleCondition, val second: StyleCondition) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            first.check(actor, screen) && second.check(actor, screen)
    }

    class Not(val first: StyleCondition) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            !first.check(actor, screen)
    }

    abstract fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor

}

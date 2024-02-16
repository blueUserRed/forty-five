package com.fourinachamber.fortyfive.screen.general.styles

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.HoverStateActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import kotlin.reflect.KClass

interface StyledActor : HoverStateActor {

    var styleManager: StyleManager?

    fun initStyles(screen: OnjScreen)

    fun enterActorState(s: String) {
        styleManager?.enterActorState(s)
    }

    fun leaveActorState(s: String) {
        styleManager?.leaveActorState(s)
    }

    /**
     * checks if the actor is in an actorstate / has an actorstate
     */
    fun inActorState(s: String): Boolean {
        return styleManager?.actorStates?.contains(s) == true
    }
}

class StyleManager(val actor: Actor, val node: YogaNode) {

    private val _styleProperties: MutableList<StyleProperty<*, *>> = mutableListOf()

    private val _actorStates: MutableSet<String> = mutableSetOf()

    val actorStates: Set<String>
        get() = _actorStates
    val styleProperties: List<StyleProperty<*, *>>
        get() = _styleProperties

    fun update() {
        _styleProperties.forEach { it.update(node) }
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

    fun copyWithNode(node: YogaNode): StyleManager {
        val manager = StyleManager(actor, node)
        manager._styleProperties.addAll(this._styleProperties)
        return manager
    }

    fun enterActorState(s: String) {
        _actorStates.add(s)
    }

    fun leaveActorState(s: String) {
        _actorStates.remove(s)
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
                        FortyFiveLogger.warn(
                            logTag, "attempted to animate a property of type YogaValue, " +
                                    "but the units used are either mixed or set to auto or undefined"
                        )
                        return null
                    }
                    YogaValue(from.value + (to.value - from.value) * percent, to.unit) as T
                }

                else -> {
                    FortyFiveLogger.warn(
                        logTag, "attempted to animate property of type ${type.simpleName}, " +
                                "which currently cannot be interpolated"
                    )
                    null
                }
            }
        }
    }

}

abstract class StyleProperty<Target, DataType>(
    val name: String,
    val target: Target,
    val default: DataType,
    val dataTypeClass: KClass<DataType>,
    val invalidate: Boolean,
    val invalidateHierarchy: Boolean,
    val screen: OnjScreen
) where Target : Actor, Target : StyledActor, DataType : Any {

    private val _instructions: MutableList<StyleInstruction<DataType>> = mutableListOf()

    val instructions: List<StyleInstruction<DataType>>
        get() = _instructions

    private var currentInstruction: StyleInstruction<DataType>? = null

    fun update(node: YogaNode) {
        val current = get(node)
        val top = _instructions
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
        set(value, node)
        if (invalidate && target is Layout) target.invalidate()
        if (invalidateHierarchy && target is Layout) target.invalidateHierarchy()
    }

    fun addInstruction(instruction: StyleInstruction<DataType>) {
        _instructions.add(instruction)
    }

    abstract fun set(data: DataType, node: YogaNode)
    abstract fun get(node: YogaNode): DataType

}

open class StyleInstruction<DataType>(
    val data: DataType,
    val priority: Int,
    val condition: StyleCondition,
    val dataTypeClass: KClass<*>
) {

    open val value: DataType
        get() = data


    open fun onControlGained(valueBefore: DataType) {}

    open fun onControlLost() {}
    override fun toString(): String {
        return "${javaClass.simpleName}(priority:$priority, data:$data)"
    }

}

@Suppress("UNCHECKED_CAST") // data is never actually used
class ObservingStyleInstruction<DataType>(
    priority: Int,
    condition: StyleCondition,
    dataTypeClass: KClass<*>,
    private val observer: () -> DataType
) : StyleInstruction<DataType>(null as DataType, priority, condition, dataTypeClass) {

    override val value: DataType
        get() = observer()
}

class AnimatedStyleInstruction<DataType>(
    data: DataType,
    priority: Int,
    condition: StyleCondition,
    dataTypeClass: KClass<*>,
    val duration: Int,
    val interpolation: Interpolation,
    val delay: Int,
) : StyleInstruction<DataType>(data, priority, condition, dataTypeClass) {

    private var startValue: DataType? = null
    private var controlGainedTime: Long = 0L
    private var startTime: Long = 0L

    override val value: DataType
        get() {
            val startValue = startValue ?: return data
            if (controlGainedTime == 0L) return data
            if (startTime == 0L) {
                startTime = controlGainedTime + delay
            }
            val now = TimeUtils.millis()
            if (now > startTime + duration) return data
            if (now < startTime) return startValue
            val percent = (now.toDouble() - startTime.toDouble()) / duration.toDouble()
            return StyleManager.lerpStyleData(
                dataTypeClass,
                startValue,
                data,
                percent.toFloat()
            ) ?: run {
                this.startValue = null
                controlGainedTime = 0L
                startTime = 0L
                data
            }
        }

    override fun onControlGained(valueBefore: DataType) {
        startValue = valueBefore
        startTime = 0L
        controlGainedTime = TimeUtils.millis()
    }

    override fun onControlLost() {
        startValue = null
        controlGainedTime = 0
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

    class IsActorHoveredOver(val actorName: String) : StyleCondition() {

        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            (
                (screen.namedActorOrError(actorName) as? HoverStateActor)
                ?: throw RuntimeException("actor $actorName must be a HoverStateActor")
            ).isHoveredOver
    }

    class ScreenState(val state: String) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            state in screen.screenState
    }

    class InBiome(val name: String) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor =
            name == MapManager.currentDetailMap.biome
    }

    class ActorState(val state: String) : StyleCondition() {
        override fun <T> check(actor: T, screen: OnjScreen): Boolean where T : Actor, T : StyledActor {
            return actor.inActorState(state)
        }
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

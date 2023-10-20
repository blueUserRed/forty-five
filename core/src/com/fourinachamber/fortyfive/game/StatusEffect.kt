package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.floor

abstract class StatusEffect(
    private val iconHandle: ResourceHandle,
    private val iconScale: Float
) {

    lateinit var icon: CustomImageActor
        private set

    private var isIconInitialised: Boolean = false

    protected lateinit var controller: GameController

    abstract val damageType: StatusEffectType

    open val blocksStatusEffects: List<StatusEffectType> = listOf()

    fun initIcon(gameController: GameController) {
        icon = CustomImageActor(iconHandle, gameController.curScreen)
        icon.setScale(iconScale)
        icon.reportDimensionsWithScaling = true
        icon.ignoreScalingWhenDrawing = true
        isIconInitialised = true
    }

    open fun start(controller: GameController) {
        this.controller = controller
    }

    open fun executeAfterRotation(rotation: GameController.RevolverRotation, target: StatusEffectTarget): Timeline? = null

    open fun executeOnNewTurn(target: StatusEffectTarget): Timeline? = null

    open fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline? = null

    abstract fun canStackWith(other: StatusEffect): Boolean

    abstract fun stack(other: StatusEffect)

    abstract fun isStillValid(): Boolean

    abstract fun getDisplayText(): String

    abstract fun copy(): StatusEffect

    abstract override fun equals(other: Any?): Boolean
}
abstract class RotationBasedStatusEffect(
    iconHandle: ResourceHandle,
    iconScale: Float,
    duration: Int
) : StatusEffect(iconHandle, iconScale) {

    var duration: Int = duration
        private set

    var continueForever: Boolean = false
        private set

    var rotationOnEffectStart = -1
        private set

    override fun start(controller: GameController) {
        super.start(controller)
        rotationOnEffectStart = controller.revolverRotationCounter
    }

    override fun isStillValid(): Boolean =
        continueForever || controller.revolverRotationCounter < rotationOnEffectStart + duration

    override fun getDisplayText(): String = if (!continueForever) {
        "${rotationOnEffectStart + duration - controller.revolverRotationCounter} rotations"
    } else {
        "∞"
    }

    protected fun extendDuration(extension: Int) {
        duration += extension
    }

    protected fun continueForever() {
        continueForever = true
    }

    protected fun stackRotationEffect(other: RotationBasedStatusEffect) {
        duration += other.duration
        if (other.continueForever) continueForever = true
    }
}


abstract class TurnBasedStatusEffect(
    iconHandle: ResourceHandle,
    iconScale: Float,
    duration: Int
) : StatusEffect(iconHandle, iconScale) {

    var turnOnEffectStart = -1
        private set

    var duration: Int = duration
        private set

    var continueForever: Boolean = false
        private set

    override fun start(controller: GameController) {
        super.start(controller)
        turnOnEffectStart = controller.turnCounter
    }

    override fun isStillValid(): Boolean =
        continueForever || controller.turnCounter < turnOnEffectStart + duration

    override fun getDisplayText(): String = if (!continueForever) {
        "${turnOnEffectStart + duration - controller.turnCounter} turns"
    } else {
        "∞"
    }

    protected fun extendDuration(extension: Int) {
        duration += extension
    }

    protected fun continueForever() {
        continueForever = true
    }

    protected fun stackTurnEffect(other: TurnBasedStatusEffect) {
        duration += other.duration
        if (other.continueForever) continueForever = true
    }
}

class Burning(
    rotations: Int,
    private val percent: Float,
    continueForever: Boolean
) : RotationBasedStatusEffect(
    GraphicsConfig.iconName("burning"),
    GraphicsConfig.iconScale("burning"),
    rotations,
) {

    init {
        if (continueForever) continueForever()
    }

    override val damageType: StatusEffectType = StatusEffectType.FIRE

    override fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline = Timeline.timeline {
        if (target.isBlocked(this@Burning, controller)) return Timeline()
        val additionalDamage = floor(damage * percent).toInt()
        include(target.damage(additionalDamage, controller))
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Burning && other.percent == percent

    override fun stack(other: StatusEffect) {
        other as Burning
        stackRotationEffect(other)
    }

    override fun copy(): StatusEffect = Burning(duration, percent, continueForever)

    override fun equals(other: Any?): Boolean = other is Burning
}

class Poison(
    turns: Int,
    private var damage: Int
) : TurnBasedStatusEffect(
    GraphicsConfig.iconName("poison"),
    GraphicsConfig.iconScale("poison"),
    turns
) {

    override val damageType: StatusEffectType = StatusEffectType.POISON

    override fun executeOnNewTurn(target: StatusEffectTarget): Timeline {
        if (target.isBlocked(this, controller)) return Timeline()
        return target.damage(damage, controller)
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Poison

    override fun stack(other: StatusEffect) {
        other as Poison
        stackTurnEffect(other)
        damage += other.damage
    }

    override fun getDisplayText(): String =
        "$damage damage / ${if (continueForever) "∞" else "${turnOnEffectStart + duration - controller.turnCounter} turns"}"

    override fun copy(): StatusEffect = Poison(duration, damage)

    override fun equals(other: Any?): Boolean = other is Poison
}

class FireResistance(
    turns: Int
) : TurnBasedStatusEffect(
    GraphicsConfig.iconName("fireResistance"),
    GraphicsConfig.iconScale("fireResistance"),
    turns
) {

    override val damageType: StatusEffectType = StatusEffectType.BLOCKING
    override val blocksStatusEffects: List<StatusEffectType> = listOf(StatusEffectType.FIRE)

    override fun canStackWith(other: StatusEffect): Boolean = other is FireResistance

    override fun stack(other: StatusEffect) {
        other as FireResistance
        stackTurnEffect(other)
    }

    override fun copy(): StatusEffect = FireResistance(duration)

    override fun equals(other: Any?): Boolean = other is FireResistance

}

class Bewitched(
    private val turns: Int,
    private val rotations: Int
) : StatusEffect(
    GraphicsConfig.iconName("bewitched"),
    GraphicsConfig.iconScale("bewitched")
) {

    override val damageType: StatusEffectType = StatusEffectType.OTHER

    private var turnOnEffectStart: Int = -1
    private var rotationOnEffectStart: Int = -1

    private var turnsDuration: Int = turns
    private var rotationDuration: Int = rotations

    override fun start(controller: GameController) {
        super.start(controller)
        turnOnEffectStart = controller.turnCounter
        rotationOnEffectStart = controller.revolverRotationCounter
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Bewitched

    override fun stack(other: StatusEffect) {
        other as Bewitched
        turnsDuration += other.turns
        rotationDuration += other.rotations
    }

    override fun isStillValid(): Boolean =
        controller.turnCounter < turnOnEffectStart + turnsDuration ||
        controller.revolverRotationCounter < rotationOnEffectStart + rotationDuration

    override fun getDisplayText(): String = "$rotationDuration rotations or $turnsDuration turns"

    override fun copy(): StatusEffect = Bewitched(turns, rotations)

    override fun equals(other: Any?): Boolean = other is Bewitched

}

enum class StatusEffectType {
    FIRE, POISON, OTHER, BLOCKING
}

sealed class StatusEffectTarget {

    class EnemyTarget(val enemy: Enemy) : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController) =
            enemy.damage(damage, triggeredByStatusEffect = true)

        override fun isBlocked(effect: StatusEffect, controller: GameController): Boolean =
            enemy.statusEffect.any { effect.damageType in it.blocksStatusEffects }
    }

    object PlayerTarget : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController): Timeline =
            controller.damagePlayerTimeline(damage, triggeredByStatusEffect = true)

        override fun isBlocked(effect: StatusEffect, controller: GameController): Boolean = controller
            .playerStatusEffects
            .any { effect.damageType in it.blocksStatusEffects }

    }

    abstract fun damage(damage: Int, controller: GameController): Timeline

    abstract fun isBlocked(effect: StatusEffect, controller: GameController): Boolean

}

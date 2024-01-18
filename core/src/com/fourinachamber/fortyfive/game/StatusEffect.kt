package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
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

    abstract val effectType: StatusEffectType

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

    open fun modifyDamage(damage: Int): Int = damage

    open fun executeAfterRotation(rotation: RevolverRotation, target: StatusEffectTarget): Timeline? = null

    open fun executeOnNewTurn(target: StatusEffectTarget): Timeline? = null

    open fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline? = null

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    abstract fun canStackWith(other: StatusEffect): Boolean

    abstract fun stack(other: StatusEffect)

    abstract fun isStillValid(): Boolean

    abstract fun getDisplayText(): String

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

    override val effectType: StatusEffectType = StatusEffectType.FIRE

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

    override val effectType: StatusEffectType = StatusEffectType.POISON

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

    override fun equals(other: Any?): Boolean = other is Poison
}

class FireResistance(
    turns: Int
) : TurnBasedStatusEffect(
    GraphicsConfig.iconName("fireResistance"),
    GraphicsConfig.iconScale("fireResistance"),
    turns
) {

    override val effectType: StatusEffectType = StatusEffectType.BLOCKING
    override val blocksStatusEffects: List<StatusEffectType> = listOf(StatusEffectType.FIRE)

    override fun canStackWith(other: StatusEffect): Boolean = other is FireResistance

    override fun stack(other: StatusEffect) {
        other as FireResistance
        stackTurnEffect(other)
    }

    override fun equals(other: Any?): Boolean = other is FireResistance

}

class Bewitched(
    private val turns: Int,
    private val rotations: Int
) : StatusEffect(
    GraphicsConfig.iconName("bewitched"),
    GraphicsConfig.iconScale("bewitched")
) {

    override val effectType: StatusEffectType = StatusEffectType.WITCH

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
        controller.turnCounter < turnOnEffectStart + turnsDuration &&
        controller.revolverRotationCounter < rotationOnEffectStart + rotationDuration

    override fun getDisplayText(): String =
            "${rotationOnEffectStart + rotationDuration - controller.revolverRotationCounter} rotations or " +
            "${turnOnEffectStart + turnsDuration - controller.turnCounter} turns"

    override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.Left(1)

    override fun equals(other: Any?): Boolean = other is Bewitched

}

class WardOfTheWitch(
    private val amount: Int
) : StatusEffect(
    GraphicsConfig.iconName("wardOfTheWitch"),
    GraphicsConfig.iconScale("wardOfTheWitch")
) {

    override val effectType: StatusEffectType = StatusEffectType.WITCH

    override fun canStackWith(other: StatusEffect): Boolean = false

    override fun stack(other: StatusEffect) { }

    override fun isStillValid(): Boolean = true

    override fun executeAfterRotation(rotation: RevolverRotation, target: StatusEffectTarget): Timeline? {
        if (target !is StatusEffectTarget.EnemyTarget) {
            throw RuntimeException("WardOfTheWitch can only be used on enemies")
        }
        if (rotation !is RevolverRotation.Left) return null
        return target.enemy.addCoverTimeline(amount)
    }

    override fun getDisplayText(): String = "+$amount shield"

    override fun equals(other: Any?): Boolean = other is WardOfTheWitch

}

class WrathOfTheWitch(
    private val damage: Int
) : StatusEffect(
    GraphicsConfig.iconName("wrathOfTheWitch"),
    GraphicsConfig.iconScale("wrathOfTheWitch")
) {

    override val effectType: StatusEffectType = StatusEffectType.WITCH

    override fun executeAfterRotation(
        rotation: RevolverRotation,
        target: StatusEffectTarget
    ): Timeline? = if (rotation is RevolverRotation.Left) Timeline.timeline {
        if (target !is StatusEffectTarget.PlayerTarget) {
            throw RuntimeException("WrathOfTheWitch can only be used on the player")
        }
        include(controller.damagePlayerTimeline(damage, true))
    } else {
        null
    }

    override fun canStackWith(other: StatusEffect): Boolean = false

    override fun stack(other: StatusEffect) {}

    override fun isStillValid(): Boolean = true

    override fun getDisplayText(): String = "$damage dmg"

    override fun equals(other: Any?): Boolean = other is WrathOfTheWitch

}

class Shield(
    private var shield: Int
) : StatusEffect(
    GraphicsConfig.iconName("shield"),
    GraphicsConfig.iconScale("shield")
) {

    override val effectType: StatusEffectType = StatusEffectType.OTHER

    override fun canStackWith(other: StatusEffect): Boolean = other is Shield

    override fun stack(other: StatusEffect) {
        other as Shield
        shield += other.shield
    }

    override fun modifyDamage(damage: Int): Int {
        val shield = shield - damage
        this.shield = shield.coerceAtLeast(0)
        if (shield < 0) return -shield
        return 0
    }

    override fun isStillValid(): Boolean = shield > 0

    override fun getDisplayText(): String = shield.toString()

    override fun equals(other: Any?): Boolean = other is Shield

}

typealias StatusEffectCreator = () -> StatusEffect

enum class StatusEffectType {
    FIRE, POISON, OTHER, BLOCKING, WITCH
}

sealed class StatusEffectTarget {

    class EnemyTarget(val enemy: Enemy) : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController) =
            enemy.damage(damage, triggeredByStatusEffect = true)

        override fun isBlocked(effect: StatusEffect, controller: GameController): Boolean =
            enemy.statusEffects.any { effect.effectType in it.blocksStatusEffects }
    }

    object PlayerTarget : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController): Timeline =
            controller.damagePlayerTimeline(damage, triggeredByStatusEffect = true)

        override fun isBlocked(effect: StatusEffect, controller: GameController): Boolean = controller
            .playerStatusEffects
            .any { effect.effectType in it.blocksStatusEffects }

    }

    abstract fun damage(damage: Int, controller: GameController): Timeline

    abstract fun isBlocked(effect: StatusEffect, controller: GameController): Boolean

}

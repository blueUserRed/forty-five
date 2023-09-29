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
}

abstract class RotationBasedStatusEffect(
    iconHandle: ResourceHandle,
    iconScale: Float,
    duration: Int
) : StatusEffect(iconHandle, iconScale) {

    var duration: Int = duration
        private set

    private var rotationOnEffectStart = -1

    override fun start(controller: GameController) {
        super.start(controller)
        rotationOnEffectStart = controller.revolverRotationCounter
    }

    override fun isStillValid(): Boolean =
        controller.revolverRotationCounter < rotationOnEffectStart + duration

    override fun getDisplayText(): String =
        "${rotationOnEffectStart + duration - controller.revolverRotationCounter} rotations"

    protected fun extendDuration(extension: Int) {
        duration += extension
    }
}

abstract class TurnBasedStatusEffect(
    iconHandle: ResourceHandle,
    iconScale: Float,
    duration: Int
) : StatusEffect(iconHandle, iconScale) {

    private var turnOnEffectStart = -1

    var duration: Int = duration
        private set

    override fun start(controller: GameController) {
        super.start(controller)
        turnOnEffectStart = controller.turnCounter
    }

    override fun isStillValid(): Boolean =
        controller.turnCounter < turnOnEffectStart + duration

    override fun getDisplayText(): String =
        "${turnOnEffectStart + duration - controller.turnCounter} turns"

    protected fun extendDuration(extension: Int) {
        duration += extension
    }
}

class Burning(
    rotations: Int,
    private val percent: Float,
) : RotationBasedStatusEffect(
    GraphicsConfig.iconName("burning"),
    GraphicsConfig.iconScale("burning"),
    rotations,
) {

    override fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline = Timeline.timeline {
        val additionalDamage = floor(damage * percent).toInt()
        include(target.damage(additionalDamage, controller))
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Burning && other.percent == percent

    override fun stack(other: StatusEffect) {
        other as Burning
        extendDuration(other.duration)
    }

    override fun copy(): StatusEffect = Burning(duration, percent)
}

sealed class StatusEffectTarget {

    class EnemyTarget(val enemy: Enemy) : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController) = enemy.damage(damage)
    }

    object PlayerTarget : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController): Timeline = controller.damagePlayerTimeline(damage)
    }

    abstract fun damage(damage: Int, controller: GameController): Timeline

}

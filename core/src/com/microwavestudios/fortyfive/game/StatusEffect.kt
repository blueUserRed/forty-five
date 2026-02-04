package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.graphics.Color
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.math.floor
import kotlin.math.min

abstract class StatusEffect(
    val iconHandle: ResourceHandle,
) {

    abstract val name: String

    protected lateinit var controller: GameController

    abstract val effectType: StatusEffectType

    open val blocksStatusEffects: List<StatusEffectType> = listOf()

    open fun start(controller: GameController) {
        this.controller = controller
    }

    open fun modifyDamage(damage: Int): Int = damage

    open fun executeAfterRotation(rotation: RevolverRotation, target: StatusEffectTarget): Timeline? = null

    open fun executeOnNewTurn(target: StatusEffectTarget): Timeline? = null

    open fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline? = null

    open fun executeAfterShot(): Timeline? = null

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    open fun additionalEnemyDamage(damage: Int, target: StatusEffectTarget): Int = 0

    open fun disableEverlasting(): Boolean = false

    open fun onEnemyAttack() {}

    open fun reevaluateEnemyAttack(): Boolean = false

    abstract fun canStackWith(other: StatusEffect): Boolean

    abstract fun stack(other: StatusEffect)

    abstract fun isStillValid(): Boolean

    abstract fun getDisplayText(): String

    abstract override fun equals(other: Any?): Boolean

    override fun hashCode(): Int {
        return this::class.qualifiedName?.hashCode() ?: 0
    }
}
abstract class RotationBasedStatusEffect(
    iconHandle: ResourceHandle,
    duration: Int,
    private val skipFirstRotation: Boolean
) : StatusEffect(iconHandle) {

    var duration: Int = duration
        private set

    var continueForever: Boolean = false
        private set

    var rotationOnEffectStart = -1
        private set

    override fun start(controller: GameController) {
        super.start(controller)
        rotationOnEffectStart = controller.revolverRotationCounter
        if (skipFirstRotation) rotationOnEffectStart++
    }

    override fun isStillValid(): Boolean =
        continueForever || controller.revolverRotationCounter < rotationOnEffectStart + duration

    override fun getDisplayText(): String = if (!continueForever) {
        val rotations = min(rotationOnEffectStart + duration - controller.revolverRotationCounter, duration)
        rotations.toString()
    } else {
        "inf"
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
    duration: Int
) : StatusEffect(iconHandle) {

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
        val turns = turnOnEffectStart + duration - controller.turnCounter
        turns.toString()
    } else {
        "inf"
    }

    protected fun extendDuration(extension: Int) {
        duration += extension
    }

    protected fun reduceDuration(extension: Int) {
        duration -= extension
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
    continueForever: Boolean,
    skipFirstRotation: Boolean,
) : RotationBasedStatusEffect(
    GraphicsConfig.iconName("burning"),
    rotations,
    skipFirstRotation,
) {

    init {
        if (continueForever) continueForever()
    }

    override val name: String = "burning"

    override val effectType: StatusEffectType = StatusEffectType.FIRE

    override fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline = Timeline.timeline {
        if (target is StatusEffectTarget.PlayerTarget) {
            FortyFive.logger.warn("BurningStatus", "Burning should only be used on the enemy, consider using BurningPlayer instead")
        }
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

class BurningPlayer(
    rotations: Int,
    private val percent: Float,
    continueForever: Boolean,
    skipFirstRotation: Boolean,
) : RotationBasedStatusEffect(
    GraphicsConfig.iconName("burning"),
    rotations,
    skipFirstRotation,
) {

    init {
        if (continueForever) continueForever()
    }

    override val name: String = "burning"

    override val effectType: StatusEffectType = StatusEffectType.FIRE

    override fun canStackWith(other: StatusEffect): Boolean = other is BurningPlayer && other.percent == percent

    override fun additionalEnemyDamage(damage: Int, target: StatusEffectTarget): Int = floor(damage * percent).toInt()

    override fun stack(other: StatusEffect) {
        other as BurningPlayer
        stackRotationEffect(other)
    }

    override fun reevaluateEnemyAttack(): Boolean = true

    override fun equals(other: Any?): Boolean = other is BurningPlayer
}

class Poison(
    damage: Int,
) : StatusEffect(
    GraphicsConfig.iconName("poison"),
) {

    override val name: String = "poison"

    override val effectType: StatusEffectType = StatusEffectType.POISON

    var damage: Int = damage
        private set

    override fun executeOnNewTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        later {
            if (target.isBlocked(this@Poison, controller)) return@later
            include(target.damage(damage, controller))
            action { damage /= 2 }
        }
    }

    fun discharge(turns: Int, target: StatusEffectTarget, controller: GameController): Timeline = Timeline.timeline {
        later {
            var damageAcc = 0
            var damage = damage
            repeat(turns) {
                damageAcc += damage
                damage /= 2
            }
            this@Poison.damage = damage
            include(target.damage(damageAcc, controller))
        }
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Poison

    override fun stack(other: StatusEffect) {
        other as Poison
        damage += other.damage
    }

    override fun isStillValid(): Boolean = damage > 0

    override fun getDisplayText(): String = damage.toString()

    override fun equals(other: Any?): Boolean = other is Poison
}

class FireResistance(
    turns: Int
) : TurnBasedStatusEffect(
    GraphicsConfig.iconName("fireResistance"),
    turns
) {

    override val name: String = "fireresistance"

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
    private val rotations: Int,
    private val skipFirstRotation: Boolean,
) : StatusEffect(
    GraphicsConfig.iconName("bewitched"),
) {

    override val name: String = "bewitched"

    override val effectType: StatusEffectType = StatusEffectType.WITCH

    private var turnOnEffectStart: Int = -1
    private var rotationOnEffectStart: Int = -1

    private var turnsDuration: Int = turns
    private var rotationDuration: Int = rotations

    override fun start(controller: GameController) {
        super.start(controller)
        turnOnEffectStart = controller.turnCounter
        rotationOnEffectStart = controller.revolverRotationCounter
        if (skipFirstRotation) rotationOnEffectStart++
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

    override fun getDisplayText(): String {
        val rotations = min(
            rotationOnEffectStart + rotationDuration - controller.revolverRotationCounter,
            rotationDuration
        )
        val turns = turnOnEffectStart + turnsDuration - controller.turnCounter
        return "$turns, $rotations"
    }

    override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
        is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
        is RevolverRotation.Left -> RevolverRotation.Left(rotation.amount)
        else -> rotation
    }

    override fun equals(other: Any?): Boolean = other is Bewitched

}

class Shield(
    private var shield: Int
) : StatusEffect(
    GraphicsConfig.iconName("shield"),
) {

    override val name: String = "shield"

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

    override fun executeOnNewTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        action { shield /= 2 }
    }

    override fun isStillValid(): Boolean = shield > 0

    override fun getDisplayText(): String = shield.toString()

    override fun equals(other: Any?): Boolean = other is Shield

}

class Frozen(shots: Int, private val skipFirstRotation: Boolean) : StatusEffect("encounter_modifier_frost") {

    var shots: Int = shots
        private set

    private var skipped: Boolean = false

    override val name: String = "Frost"

    override val effectType: StatusEffectType = StatusEffectType.OTHER

    override fun canStackWith(other: StatusEffect): Boolean = other is Frozen

    override fun stack(other: StatusEffect) {
        shots += (other as Frozen).shots
    }

    override fun executeAfterShot(): Timeline = Timeline.timeline {
        action {
            if (skipFirstRotation && !skipped) {
                skipped = true
                return@action
            }
            shots--
        }
    }

    override fun disableEverlasting(): Boolean = true

    override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

    override fun isStillValid(): Boolean = shots > 0

    override fun getDisplayText(): String = shots.toString()

    override fun equals(other: Any?): Boolean = other is Frozen

}

class Weak(attacks: Int) : StatusEffect(GraphicsConfig.iconName("weak")) {

    private var attacks: Int = attacks

    override val name: String = "weak"
    override val effectType: StatusEffectType = StatusEffectType.OTHER

    override fun canStackWith(other: StatusEffect): Boolean = other is Weak

    override fun stack(other: StatusEffect) {
        other as Weak
        attacks += other.attacks
    }

    override fun additionalEnemyDamage(
        damage: Int,
        target: StatusEffectTarget
    ): Int = -((damage.toDouble() / 2) + 0.5).toInt()

    override fun onEnemyAttack() {
        attacks--
    }

    override fun reevaluateEnemyAttack(): Boolean = true

    override fun isStillValid(): Boolean = attacks > 0

    override fun getDisplayText(): String = attacks.toString()

    override fun equals(other: Any?): Boolean = other is Weak

}

typealias StatusEffectCreator = (GameController?, Card?, skipFirstRotation: Boolean) -> StatusEffect

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

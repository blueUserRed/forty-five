package com.microwavestudios.fortyfive.game

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.Promise
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

    open fun executeOnEndTurn(target: StatusEffectTarget): Timeline? = null

    open fun executeAfterDamage(damage: Int, target: StatusEffectTarget): Timeline? = null

    open fun executeAfterShot(): Timeline? = null

    /**
     * @param dontApplyStatusEffect resolve to stop the status effect from being applied
     */
    open fun executeBeforeStatusEffectApplied(
        statusEffect: StatusEffect,
        dontApplyStatusEffect: Promise<Unit>
    ): Timeline? = null

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    open fun additionalEnemyDamage(damage: Int, target: StatusEffectTarget): Int = 0

    open fun disableEverlasting(): Boolean = false

    open fun onEnemyDeath(target: StatusEffectTarget): Timeline? = null

    open fun canBeBlocked(): Boolean = true

    abstract fun canStackWith(other: StatusEffect): Boolean

    abstract fun stack(other: StatusEffect)

    abstract fun isStillValid(): Boolean

    abstract fun getDisplayText(): String

    abstract override fun equals(other: Any?): Boolean

    override fun hashCode(): Int {
        return this::class.qualifiedName?.hashCode() ?: 0
    }

    abstract fun increment(amount: Int)

    abstract fun parameterSum(): Int

    abstract fun toDisplayString(): String


    protected fun statusTemplate(name: String): String = $$"$status$$$name$status$"
}

abstract class PassiveEnemyAction(iconHandle: ResourceHandle) : StatusEffect(iconHandle) {

    private var valid: Boolean = true

    override fun isStillValid(): Boolean = valid

    override fun getDisplayText(): String = ""

    override fun increment(amount: Int) {
    }

    override fun parameterSum(): Int = 0

    override fun stack(other: StatusEffect) {
        throw RuntimeException("cant stack passive enemy actions")
    }

    override fun canStackWith(other: StatusEffect): Boolean = false

    override fun canBeBlocked(): Boolean = false

    override fun executeOnEndTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        action { valid = false }
    }
}

abstract class RotationBasedStatusEffect(
    iconHandle: ResourceHandle,
    duration: Int,
    private val skipFirstRotation: Boolean
) : StatusEffect(iconHandle) {

    var continueForever: Boolean = false
        private set

    private var hadFirstRotation = false

    var currentDuration: Int = duration
        private set

    override fun isStillValid(): Boolean =
        continueForever || currentDuration > 0

    override fun executeAfterRotation(
        rotation: RevolverRotation,
        target: StatusEffectTarget
    ): Timeline = Timeline.timeline {
        action {
            if (skipFirstRotation && !hadFirstRotation) {
                hadFirstRotation = true
                return@action
            }
            currentDuration -= rotation.amount
            currentDuration = currentDuration.coerceAtLeast(0)
        }
    }

    override fun getDisplayText(): String = if (!continueForever) {
        currentDuration.toString()
    } else {
        "inf"
    }

    protected fun extendDuration(extension: Int) {
        currentDuration += extension
    }

    protected fun continueForever() {
        continueForever = true
    }

    protected fun stackRotationEffect(other: RotationBasedStatusEffect) {
        currentDuration += other.currentDuration
        if (other.continueForever) continueForever = true
    }

    override fun increment(amount: Int) {
        extendDuration(amount)
    }

    override fun parameterSum(): Int = if (!continueForever) {
        currentDuration
    } else {
        0
    }
}

abstract class TurnBasedStatusEffect(
    iconHandle: ResourceHandle,
    duration: Int
) : StatusEffect(iconHandle) {

    private var currentDuration: Int = duration

    var continueForever: Boolean = false
        private set

    override fun isStillValid(): Boolean =
        continueForever || currentDuration > 0

    override fun executeOnEndTurn(target: StatusEffectTarget): Timeline? = Timeline.timeline {
        action {
            currentDuration = (currentDuration - 1).coerceAtLeast(0)
        }
    }

    override fun getDisplayText(): String = if (!continueForever) {
        currentDuration.toString()
    } else {
        "inf"
    }

    protected fun extendDuration(extension: Int) {
        currentDuration += extension
    }

    protected fun reduceDuration(extension: Int) {
        currentDuration -= extension
    }

    protected fun continueForever() {
        continueForever = true
    }

    protected fun stackTurnEffect(other: TurnBasedStatusEffect) {
        currentDuration += other.currentDuration
        if (other.continueForever) continueForever = true
    }

    override fun increment(amount: Int) {
        extendDuration(amount)
    }

    override fun parameterSum(): Int = if (!continueForever) {
        currentDuration
    } else {
        0
    }
}

class Burning(
    rotations: Int,
    val percent: Float,
    continueForever: Boolean,
    val skipFirstRotation: Boolean,
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

    override fun toDisplayString(): String = "${statusTemplate("BURNING")} (${getDisplayText()})"

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

    override fun toDisplayString(): String = "${statusTemplate("BURNING")} (${getDisplayText()})"

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

    override fun executeOnEndTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        later {
            if (target.isBlocked(this@Poison, controller)) return@later
            delay(200)
            include(target.damage(damage, controller))
            action { damage /= 2 }
            delay(600)
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

    fun removeValue(amount: Int): Timeline = Timeline.timeline {
        action { damage = (damage - amount).coerceAtLeast(0) }
    }

    override fun canStackWith(other: StatusEffect): Boolean = other is Poison

    override fun stack(other: StatusEffect) {
        other as Poison
        damage += other.damage
    }

    override fun increment(amount: Int) {
        damage += amount
    }

    override fun parameterSum(): Int = damage

    override fun isStillValid(): Boolean = damage > 0

    override fun getDisplayText(): String = damage.toString()

    override fun toDisplayString(): String = "${statusTemplate("POISON")} (${getDisplayText()})"

    override fun equals(other: Any?): Boolean = other is Poison
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

    override fun toDisplayString(): String = "${statusTemplate("BEWITCHED")} (${getDisplayText()})"

    override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
        is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
        is RevolverRotation.Left -> RevolverRotation.Left(rotation.amount)
        else -> rotation
    }

    override fun increment(amount: Int) {
        turnsDuration += amount
        rotationDuration += amount
    }

    override fun parameterSum(): Int {
        val rotations = min(
            rotationOnEffectStart + rotationDuration - controller.revolverRotationCounter,
            rotationDuration
        )
        val turns = turnOnEffectStart + turnsDuration - controller.turnCounter
        return rotations + turns
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

    override fun executeOnEndTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        action { shield /= 2 }
    }

    override fun isStillValid(): Boolean = shield > 0

    override fun getDisplayText(): String = shield.toString()

    override fun toDisplayString(): String = "${statusTemplate("SHIELD")} (${getDisplayText()})"

    override fun increment(amount: Int) {
        shield += amount
    }

    override fun parameterSum(): Int = shield

    override fun equals(other: Any?): Boolean = other is Shield

}

class Frozen(shots: Int, private val skipFirstRotation: Boolean) : StatusEffect("encounter_modifier_frost") {

    var shots: Int = shots
        private set

    private var skipped: Boolean = false

    private val active: Boolean
        get() = !skipFirstRotation || skipped

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

    override fun disableEverlasting(): Boolean = active

    override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation =
        if (active) RevolverRotation.None else rotation

    override fun isStillValid(): Boolean = shots > 0

    override fun getDisplayText(): String = shots.toString()

    override fun toDisplayString(): String = "${statusTemplate("FROZEN")} (${getDisplayText()})"

    override fun increment(amount: Int) {
        shots += amount
    }

    override fun parameterSum(): Int = shots

    override fun equals(other: Any?): Boolean = other is Frozen

}

class Weak(turns: Int) : TurnBasedStatusEffect(GraphicsConfig.iconName("weak"), turns) {

    override val name: String = "weak"
    override val effectType: StatusEffectType = StatusEffectType.OTHER

    override fun canStackWith(other: StatusEffect): Boolean = other is Weak

    override fun stack(other: StatusEffect) {
        other as Weak
        stackTurnEffect(other)
    }

    override fun additionalEnemyDamage(
        damage: Int,
        target: StatusEffectTarget
    ): Int = -((damage.toDouble() / 2) + 0.5).toInt()

    override fun toDisplayString(): String = "${statusTemplate("WEAK")} (${getDisplayText()})"

    override fun equals(other: Any?): Boolean = other is Weak

}

class Bounty(turns: Int, reserves: Int) : StatusEffect(GraphicsConfig.iconName("bounty")) {

    var turns: Int = turns
        private set

    var reserves: Int = reserves
        private set

    private var enemyDied: Boolean = false

    override val name: String = "bounty"
    override val effectType: StatusEffectType = StatusEffectType.OTHER

    override fun canStackWith(other: StatusEffect): Boolean = other is Bounty

    override fun stack(other: StatusEffect) {
        other as Bounty
        turns = other.turns
        reserves = other.reserves
    }

    override fun executeOnEndTurn(target: StatusEffectTarget): Timeline = Timeline.timeline {
        action { turns-- }
    }

    override fun onEnemyDeath(target: StatusEffectTarget): Timeline = Timeline.timeline {
        require(target is StatusEffectTarget.EnemyTarget) { "Bounty can only be used on enemy" }
        action {
            enemyDied = true
            controller.gainReserves(reserves, target.enemy.actor?.let { { it } })
        }
    }

    override fun isStillValid(): Boolean = turns > 0 && !enemyDied

    override fun getDisplayText(): String = "$turns, $reserves"

    override fun toDisplayString(): String = "${statusTemplate("BOUNTY")} (${getDisplayText()})"

    override fun equals(other: Any?): Boolean = other is Bounty

    override fun increment(amount: Int) {
        turns += amount
        reserves += amount
    }

    override fun parameterSum(): Int = turns + reserves
}

class HeatRepellent : PassiveEnemyAction("encounter_modifier_frost") {

    override val name: String = "heatrepellent"

    override val effectType: StatusEffectType = StatusEffectType.FIRE

    override fun executeBeforeStatusEffectApplied(
        statusEffect: StatusEffect,
        dontApplyStatusEffect: Promise<Unit>
    ): Timeline = Timeline.timeline {
        if (statusEffect !is Burning) return@timeline
        val newEffect = BurningPlayer(
            statusEffect.currentDuration,
            statusEffect.percent,
            statusEffect.continueForever,
            statusEffect.skipFirstRotation
        )
        include(controller.tryApplyStatusEffectToPlayerTimeline(newEffect, null))
    }

    override fun getDisplayText(): String = "inf"

    override fun toDisplayString(): String = statusTemplate("HEAT REPELLENT")

    override fun isStillValid(): Boolean = true

    override fun equals(other: Any?): Boolean = other is HeatRepellent
}

class WardOfTheWitch : PassiveEnemyAction("encounter_modifier_frost") {

    override val name: String = "wardofthewitch"

    override val effectType: StatusEffectType = StatusEffectType.WITCH

    override fun executeAfterRotation(
        rotation: RevolverRotation,
        target: StatusEffectTarget
    ): Timeline = Timeline.timeline {
        require(target is StatusEffectTarget.EnemyTarget)
        if (rotation.amount != 0) include(target.enemy.addCoverTimeline(rotation.amount))
    }

    override fun toDisplayString(): String = statusTemplate("WARD OF THE WITCH")

    override fun equals(other: Any?): Boolean = other is WardOfTheWitch

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
            effect.canBeBlocked() && enemy.statusEffects.any { effect.effectType in it.blocksStatusEffects }
    }

    object PlayerTarget : StatusEffectTarget() {

        override fun damage(damage: Int, controller: GameController): Timeline =
            controller.damagePlayerTimeline(damage, triggeredByStatusEffect = true)

        override fun isBlocked(effect: StatusEffect, controller: GameController): Boolean = effect.canBeBlocked() &&
            controller
            .playerStatusEffects
            .any { effect.effectType in it.blocksStatusEffects }

    }

    abstract fun damage(damage: Int, controller: GameController): Timeline

    abstract fun isBlocked(effect: StatusEffect, controller: GameController): Boolean

}

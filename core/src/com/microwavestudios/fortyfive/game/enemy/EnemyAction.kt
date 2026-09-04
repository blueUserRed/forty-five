package com.microwavestudios.fortyfive.game.enemy

import com.microwavestudios.fortyfive.game.BurningPlayer
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.StatusEffectCreator
import com.microwavestudios.fortyfive.game.StatusEffectTarget
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

abstract class EnemyAction(protected val data: EnemyActionData?) {

    abstract val defaultTitle: String
    abstract val defaultDescription: String
    abstract val defaultIcon: ResourceHandle

    abstract val indicatorText: String

    val title: String
        get() = data?.overrideTitle ?: defaultTitle

    val description: String
        get() = data?.overrideDescription ?: defaultDescription

    val icon: String
        get() = data?.overrideIcon ?: defaultIcon

    open fun onSelected(
        enemy: Enemy,
        controller: GameController
    ) {}

    open fun onShow(
        enemy: Enemy,
        controller: GameController
    ) {}

    abstract fun getTimeline(
        enemy: Enemy,
        controller: GameController
    ): Timeline

    abstract fun copy(data: EnemyActionData?): EnemyAction

    protected fun getAdditionalDamage(
        originalDamage: Int,
        data: EnemyActionData
    ): List<Pair<String, Int>> {
        val controller = data.controller
        val enemy = data.enemy
        val playerModifiers = controller
            .playerStatusEffects
            .zip { it.additionalEnemyDamage(originalDamage, StatusEffectTarget.PlayerTarget) }
            .filter { it.second != 0 }
            .mapFirst { it.iconHandle }
        val enemyModifiers = enemy
            .statusEffects
            .zip { it.additionalEnemyDamage(originalDamage, StatusEffectTarget.EnemyTarget(enemy)) }
            .filter { it.second != 0 }
            .mapFirst { it.iconHandle }
        return playerModifiers + enemyModifiers
    }

    private interface DamageAction {

        fun buildDefaultDescription(
            damage: Int?,
            data: EnemyActionData?,
            isPiercing: Boolean,
            additionalExplanation: String?,
            additionalFn: (Int, EnemyActionData) -> List<Pair<String, Int>>
        ): String {
            data ?: return ""
            val baseDmg = damage ?: return ""
            val additional = additionalFn(baseDmg, data)
            val damage = baseDmg + additional.sumOf { it.second }
            val builder = StringBuilder("The enemy deals $damage damage\n\n")
            if (isPiercing) {
                builder.append("Piercing: Shield will have no effect, but the damage can still be parried\n")
            }
            additionalExplanation?.let { builder.append("$it\n") }
            if (additional.isEmpty()) return builder.toString()
            builder.append("\nbase: $baseDmg\n")
            additional.forEach { (icon, dmg) ->
                builder.append(if (dmg < 0) "-" else "+")
                builder.append(dmg)
                builder.append("§§$icon§§\n")
            }
            return builder.toString()
        }

        fun buildIndicatorText(
            damage: Int?,
            data: EnemyActionData?,
            isPiercing: Boolean,
            additionalFn: (Int, EnemyActionData) -> List<Pair<String, Int>>
        ): String {
            damage ?: return ""
            data ?: return ""
            val additional = additionalFn(damage, data).sumOf { it.second }
            return when {
                additional > 0 -> "$damage\$enemyDamageIncrease\$+$additional\$enemyDamageIncrease\$"
                additional < 0 -> "$damage\$enemyDamageDecrease\$-$additional\$enemyDamageDecrease\$"
                else -> damage.toString()
            }
        }
    }

    class DamagePlayer(
        val min: Int, val max: Int,
        val isPiercing: Boolean,
        data: EnemyActionData?
    ) : EnemyAction(data), DamageAction {

        private var damage: Int? = null

        override val defaultTitle: String = "Attack"
        override val defaultIcon: ResourceHandle = "enemy_action_damage"

        override val defaultDescription: String
            get() = buildDefaultDescription(damage, data, isPiercing, null, ::getAdditionalDamage)

        override val indicatorText: String
            get() = buildIndicatorText(damage, data, isPiercing, ::getAdditionalDamage)

        override fun onSelected(
            enemy: Enemy,
            controller: GameController
        ) {
            requireNull(damage) { "Cant reuse EnemyActions" }
            requireNotNull(data) { "EnemyAction must be copied with appropriate data before it can be used" }
            val baseDamage = (min..max).random(controller.random)
            damage = (baseDamage * data.difficulty).toInt()
        }

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline { later {
            val originalDamage = damage!!
            val additional = getAdditionalDamage(originalDamage, data!!).sumOf { it.second }
            val damage = originalDamage + additional
            val parryResult = Promise<Int>()
            val texts: (remainingDamage: Int) -> Pair<String, String> = { remainingDamage ->
                "Parrying will let $remainingDamage damage through" to
                "Passing will let $damage damage through"
            }
            include(controller.askParryTimeline(damage, parryResult, texts))
            later {
                val result = parryResult.getOrError()
                if (result != 0) include(controller.enemyAttackTimeline(result, enemy, isPiercing))
            }
        } }

        override fun copy(data: EnemyActionData?) = DamagePlayer(min, max, isPiercing, data)
    }

    class DamagePlayerVariable(
        val damage: EnemyActionValue,
        val isPiercing: Boolean,
        val additionalExplanation: String?,
        data: EnemyActionData?
    ) : EnemyAction(data), DamageAction {

        override val defaultTitle: String = "Attack"
        override val defaultIcon: ResourceHandle = "enemy_action_damage"

        override val defaultDescription: String
            get() = data?.let { data ->
                buildDefaultDescription(
                    damage(data.enemy, data.controller),
                    data,
                    isPiercing,
                    additionalExplanation,
                    ::getAdditionalDamage
                )
            } ?: ""

        override val indicatorText: String
            get() = data?.let { data ->
                buildIndicatorText(
                    damage(data.enemy, data.controller),
                    data,
                    isPiercing,
                    ::getAdditionalDamage
                )
            } ?: ""

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline { later {
            requireNotNull(data)
            val originalDamage = damage(data.enemy, data.controller)
            val additional = getAdditionalDamage(originalDamage, data).sumOf { it.second }
            val damage = originalDamage + additional
            val parryResult = Promise<Int>()
            val texts: (remainingDamage: Int) -> Pair<String, String> = { remainingDamage ->
                "Parrying will let $remainingDamage damage through" to
                "Passing will let $damage damage through"
            }
            include(controller.askParryTimeline(damage, parryResult, texts))
            later {
                val result = parryResult.getOrError()
                if (result != 0) include(controller.enemyAttackTimeline(result, enemy, isPiercing))
            }
        } }

        override fun copy(data: EnemyActionData?) =
            DamagePlayerVariable(damage, isPiercing, additionalExplanation, data)
    }

    class ApplyShield(val min: Int, val max: Int, data: EnemyActionData?) : EnemyAction(data) {

        private var shield: Int? = null

        override val defaultTitle: String = "Shield"

        override val defaultDescription: String
            get() = shield?.let { "The Enemy gives itself $shield shield" } ?: ""

        override val defaultIcon: ResourceHandle = "enemy_action_cover"

        override val indicatorText: String
            get() = shield?.toString() ?: ""

        override fun onSelected(
            enemy: Enemy,
            controller: GameController
        ) {
            requireNull(shield) { "Cant reuse EnemyActions" }
            requireNotNull(data)
            shield = (min..max).random(data.controller.random)
        }

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline { later {
            val shield = shield
            requireNotNull(shield)
            requireNotNull(data)
            include(data.enemy.addCoverTimeline(shield))
        } }

        override fun copy(data: EnemyActionData?): EnemyAction = ApplyShield(min, max, data)
    }

    class ParryableBurning(val min: Int, val max: Int, data: EnemyActionData?) : EnemyAction(data) {

        private var burningValue: Int? = null

        override val defaultTitle: String = "Burning"

        override val defaultDescription: String
            get() = burningValue?.let { value ->
                $$"""
                The Enemy will give you $status$BURNING$status$ ($$value).
                
                However, you will be given the chance to parry, reducing the parameter value of the Status Effect.
                """.trimIndent()
            } ?: ""

        override val defaultIcon: ResourceHandle = "enemy_action_burning"

        override val indicatorText: String
            get() = burningValue?.toString() ?: ""

        override fun onSelected(
            enemy: Enemy,
            controller: GameController
        ) {
            requireNull(burningValue) { "Cant reuse EnemyActions" }
            requireNotNull(data)
            burningValue = ((min..max).random(controller.random) * data.difficulty).toInt()
        }

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline { later {
            requireNotNull(data)
            val burningValue = burningValue
            requireNotNull(burningValue)
            val result = Promise<Int>()
            val texts: (remaining: Int) -> Pair<String, String> = { remaining ->
                "Parrying will result in Burning($remaining)" to
                "Passing will result in Burning($burningValue)"
            }
            include(controller.askParryTimeline(burningValue, result, texts))
            later {
                val newValue = result.getOrError()
                if (newValue <= 0) return@later
                val effect = BurningPlayer(newValue, 0.5f, continueForever = false, skipFirstRotation = false)
                include(controller.tryApplyStatusEffectToPlayerTimeline(effect))
            }
        } }

        override fun copy(data: EnemyActionData?): EnemyAction = ParryableBurning(min, max, data)

    }

    class GivePlayerCard(val card: CardType, data: EnemyActionData?) : EnemyAction(data) {

        override val defaultTitle: String = "You get a card!"

        override val defaultDescription: String
            get() = data?.let { data -> """
                The enemy will put a [${data.controller.titleOfCard(card)}] into you hand!
            """.trimIndent() } ?: ""

        override val defaultIcon: ResourceHandle = "enemy_action_burning"

        override val indicatorText: String = ""

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = controller.tryToPutCardsInHandTimeline(card)

        override fun copy(data: EnemyActionData?): EnemyAction = GivePlayerCard(card, data)
    }

    class PassiveAction(val statusEffect: StatusEffectCreator, data: EnemyActionData?) : EnemyAction(data) {

        override val defaultTitle: String = ""
        override val defaultDescription: String = ""

        private val dummyStatusEffect: StatusEffect = statusEffect(null, null, false)

        override val defaultIcon: ResourceHandle
            get() = dummyStatusEffect.iconHandle

        override val indicatorText: String = ""

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val effect = statusEffect(controller, null, false)
            include(controller.tryApplyStatusEffectToEnemyTimeline(effect, enemy))
        }

        override fun copy(data: EnemyActionData?): EnemyAction = PassiveAction(statusEffect, data)

    }

    class GivePlayerStatus(val statusEffect: StatusEffectCreator, data: EnemyActionData?) : EnemyAction(data) {

        private val dummyStatusEffect: StatusEffect = statusEffect(null, null, false)

        override val defaultTitle: String = ""
        override val defaultDescription: String = "The enemy gives you ${dummyStatusEffect.toDisplayString()}"

        override val defaultIcon: ResourceHandle
            get() = dummyStatusEffect.iconHandle

        override val indicatorText: String = ""

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val effect = statusEffect(controller, null, false)
            include(controller.tryApplyStatusEffectToPlayerTimeline(effect))
        }

        override fun copy(data: EnemyActionData?): EnemyAction = GivePlayerStatus(statusEffect, data)
    }

    class BewitchedLeftRight(data: EnemyActionData?) : EnemyAction(data) {

        override val defaultTitle: String = ""
        override val defaultDescription: String = ""
        override val indicatorText: String = ""

        override val defaultIcon: ResourceHandle = "enemy_action_burning"

        override fun getTimeline(
            enemy: Enemy,
            controller: GameController
        ): Timeline = Timeline.timeline { later {
            val rotations = controller.revolverRotationCountInTurn
            val rotation = if (rotations % 2 == 0) {
                RevolverRotation.Right(1)
            } else {
                RevolverRotation.Left(1)
            }
            include(controller.rotateRevolverTimeline(rotation))
        } }

        override fun copy(data: EnemyActionData?): EnemyAction = BewitchedLeftRight(data)

    }

    data class EnemyActionData(
        val isHidden: Boolean,
        val difficulty: Double,
        val enemy: Enemy,
        val controller: GameController,
        val overrideTitle: String? = null,
        val overrideDescription: String? = null,
        val overrideIcon: ResourceHandle? = null,
    )
}

typealias EnemyActionValue = (enemy: Enemy, controller: GameController) -> Int
typealias EnemyPredicate = (enemy: Enemy, controller: GameController) -> Boolean


sealed class NextEnemyAction {

    data object None : NextEnemyAction()

    class ShownEnemyAction(val action: EnemyAction) : NextEnemyAction()

    class HiddenEnemyAction(val action: EnemyAction) : NextEnemyAction()

}

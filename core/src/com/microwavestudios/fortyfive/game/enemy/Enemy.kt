package com.microwavestudios.fortyfive.game.enemy

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.max

data class EnemyPrototype(
    val name: String,
    val baseHealth: Int,
    private val creator: (health: Int) -> Enemy
) {
    fun create(health: Int): Enemy = creator(health)
}

class Enemy(
    val name: String,
    val drawableHandle: ResourceHandle,
    val health: Int,
) {

    val logTag = "enemy-$name-${++instanceCounter}"

    private var brain: EnemyBrain = NoOpEnemyBrain

    val enemyEvents: EventPipeline = EventPipeline()

    /**
     * the current lives of this enemy
     */
    var currentHealth: Int = health
        private set(value) {
            FortyFive.logger.debug(logTag, "enemy lives updated: new lives = $field ")
            field = max(value, -300)
            enemyEvents.fire(HealthChangedEvent)
        }

    val isDefeated: Boolean
        get() = currentHealth <= 0

    var currentCover: Int = 0
        private set(value) {
            field = value
            FortyFive.logger.debug(logTag, "enemy cover updated: new cover = $field")
        }

    private val _statusEffects: MutableList<StatusEffect> = mutableListOf()

    val statusEffects: List<StatusEffect>
        get() = _statusEffects

    var additionalDamage: Int = 0
        private set

    fun chooseNewAction(controller: GameController, difficulty: Double, otherActions: List<NextEnemyAction>): NextEnemyAction {
        additionalDamage = 0
        val nextAction = brain.chooseNewAction(controller, this, difficulty, otherActions)
        if (
            nextAction !is NextEnemyAction.ShownEnemyAction ||
            nextAction.action.prototype !is EnemyActionPrototype.DamagePlayer
        ) {
            val event = EnemyActionChangedEvent(nextAction, 0, null)
            enemyEvents.fire(event)
            return nextAction
        }
        val additionalDmgActions = controller
            .playerStatusEffects
            .zip { it.additionalEnemyDamage(nextAction.action.directDamageDealt, StatusEffectTarget.PlayerTarget) }
            .filter { it.second != 0 }
        if (additionalDmgActions.isEmpty()) {
            val event = EnemyActionChangedEvent(nextAction, 0, null)
            enemyEvents.fire(event)
            return nextAction
        }
        if (additionalDmgActions.size > 1) {
            FortyFive.logger.warn(logTag, "Having more than one status effect that increases enemy damage is currently not supported")
        }
        val (action, additionalDamage) = additionalDmgActions.first()
        this.additionalDamage = additionalDamage
        val event = EnemyActionChangedEvent(nextAction, additionalDamage, action.iconHandle)
        enemyEvents.fire(event)
        return nextAction
    }

    fun resolveAction(controller: GameController, difficulty: Double): EnemyAction? {
        val action = brain.resolveEnemyAction(controller, this, difficulty)
        return action
    }

    fun applyEffect(effect: StatusEffect, controller: GameController) {
        if (isDefeated) return
        FortyFive.logger.debug(logTag, "status effect $effect applied to enemy")
        for (effectToTest in _statusEffects) if (effectToTest.canStackWith(effect)) {
            FortyFive.logger.debug(logTag, "stacked with $effectToTest")
            effectToTest.stack(effect)
            return
        }
        effect.start(controller)
        _statusEffects.add(effect)
        enemyEvents.fire(StatusEffectsChangedEvent)
    }

    fun executeStatusEffectsAfterTurn(): Timeline = _statusEffects
        .mapNotNull { it.executeOnNewTurn(StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun executeStatusEffectsAfterDamage(damage: Int): Timeline = _statusEffects
        .mapNotNull { it.executeAfterDamage(damage, StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun executeStatusEffectsAfterRevolverRotation(rotation: RevolverRotation): Timeline = _statusEffects
        .mapNotNull { it.executeAfterRotation(rotation, StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun update() {
        var change = false
        _statusEffects.removeIf { effect ->
            if (!effect.isStillValid()) {
                change = true
                true
            } else {
                false
            }
        }
        if (!change) return
        enemyEvents.fire(StatusEffectsChangedEvent)
    }

    fun addCoverTimeline(amount: Int): Timeline = Timeline.timeline {
        action {
            currentCover += amount
        }
    }

    fun brainTransplant(newBrain: EnemyBrain) {
        brain = newBrain
    }


    /**
     * reduces the enemies lives by [damage]
     */
    fun damage(damage: Int, triggeredByStatusEffect: Boolean = false): Timeline = Timeline.timeline {
        var remaining = 0

        action {
            remaining = max(damage - currentCover, 0)
        }

        includeLater(
            { Timeline.timeline {
                action {
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
                }
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                action {
                    currentHealth -= remaining
                }
            } },
            { remaining != 0 }
        )

        includeLater(
            { executeStatusEffectsAfterDamage(damage) },
            { remaining != 0 && !triggeredByStatusEffect }
        )
    }

    data object HealthChangedEvent
    data object StatusEffectsChangedEvent
    data class PlayChargeAnimationEvent(val timeline: Promise<Timeline> = Promise())
    data class EnemyActionChangedEvent(
        val nextAction: NextEnemyAction,
        val additionalDamage: Int,
        val additionalDamageIcon: String?
    )

    companion object {

        private var instanceCounter = 0

        fun readEnemies(arr: OnjArray): List<EnemyPrototype> = arr
            .value
            .map { it as OnjObject }
            .map {
                EnemyPrototype(
                    it.get<String>("name"),
                    it.get<Long>("baseHealth").toInt(),
                ) { health -> readEnemy(it, health) }
            }
        
        fun readEnemy(onj: OnjObject, health: Int): Enemy {
            val drawableHandle = onj.get<String>("texture")
            val enemy = Enemy(
                onj.get<String>("name"),
                drawableHandle,
                health
            )
            val brain = EnemyBrain.fromOnj(onj.get<OnjNamedObject>("brain"), enemy)
            enemy.brainTransplant(brain)
            return enemy
        }

    }

}

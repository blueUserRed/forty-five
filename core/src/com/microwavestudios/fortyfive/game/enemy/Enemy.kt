package com.microwavestudios.fortyfive.game.enemy

import com.badlogic.gdx.scenes.scene2d.Actor
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
import kotlin.math.log

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

    val logTag = "$name-${++instanceCounter}"

    private var brain: EnemyBrain = NoOpEnemyBrain

    val enemyEvents: EventPipeline = EventPipeline()

    var actor: Actor? = null

    /**
     * the current lives of this enemy
     */
    var currentHealth: Int = health
        private set(value) {
            FortyFive.logger.debug(logTag, "enemy lives updated: new lives = $field ")
            val defeated = field > 0 && value <= 0
            field = max(value, -300)
            enemyEvents.fire(HealthChangedEvent)
            if (defeated) enemyEvents.fire(EnemyDefeated)
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

    private var nextActionCreator: EnemyActionCreator? = null
    private var nextActionShown: Boolean = true
    private var createdNextAction: EnemyAction? = null
    private var createdWithDifficulty: Double = 1.0

    fun chooseNewAction(
        controller: GameController,
        difficulty: Double,
        otherActions: List<Pair<EnemyActionPrototype, Boolean>>
    ): Pair<EnemyActionPrototype, Boolean>? {
        createdWithDifficulty = difficulty
        val (actionProto, shown) = brain.chooseNewAction(controller, this, difficulty, otherActions) ?: run {
            nextActionCreator = null
            createdNextAction = null
            enemyEvents.fire(EnemyActionChangedEvent(NextEnemyAction.None))
            return null
        }
        val creator = actionProto.newCreator(controller, difficulty)
        val created = creator()
        nextActionCreator = creator
        createdNextAction = created
        nextActionShown = shown

        val nextAction = if (shown) NextEnemyAction.ShownEnemyAction(created) else NextEnemyAction.HiddenEnemyAction
        val event = EnemyActionChangedEvent(nextAction)
        enemyEvents.fire(event)
        return actionProto to shown
    }

    fun reevaluateAction(controller: GameController) {
        val newAction = nextActionCreator?.invoke()
        createdNextAction = newAction
        val nextAction = when {
            newAction == null -> NextEnemyAction.None
            nextActionShown -> NextEnemyAction.ShownEnemyAction(newAction)
            else -> NextEnemyAction.HiddenEnemyAction
        }
        val event = EnemyActionChangedEvent(nextAction)
        enemyEvents.fire(event)
    }

    fun resolveAction(controller: GameController, difficulty: Double): EnemyAction? {
        brain.onNewTurn(controller, this)
        return createdNextAction
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

    fun executeStatusEffectsAfterShot(): Timeline = _statusEffects
        .mapNotNull { it.executeAfterShot() }
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
    fun damage(damage: Int, triggeredByStatusEffect: Boolean = false, isPiercing: Boolean = false): Timeline = Timeline.timeline {
        var remaining = 0
        var healthBefore = 0
        action { healthBefore = currentHealth }

        if (!isPiercing) {
            later {
                remaining = max(damage - currentCover, 0)
                if (currentCover == 0) return@later
                currentCover -= damage
                if (currentCover < 0) currentCover = 0
            }
        } else {
            action {
                remaining = max(damage, 0)
            }
        }

        var wasDefeated = false

        later {
            if (remaining != 0) currentHealth -= remaining
            wasDefeated = healthBefore > 0 && currentHealth <= 0
        }

        includeLater(
            { executeStatusEffectsAfterDamage(damage) },
            { remaining != 0 && !triggeredByStatusEffect }
        )

        later {
            if (!wasDefeated) return@later
            _statusEffects
                .mapNotNull { it.onEnemyDeath(StatusEffectTarget.EnemyTarget(this@Enemy)) }
                .collectTimeline()
                .let { include(it) }
        }
    }

    override fun toString(): String {
        return logTag
    }

    data object HealthChangedEvent
    data object EnemyDefeated
    data object StatusEffectsChangedEvent
    data class PlayChargeAnimationEvent(val timeline: Promise<Timeline> = Promise())
    data class EnemyActionChangedEvent(val nextAction: NextEnemyAction)

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

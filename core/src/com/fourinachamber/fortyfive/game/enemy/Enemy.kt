package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.animation.AnimationDrawable
import com.fourinachamber.fortyfive.animation.createAnimation
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.RevolverRotation
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.gameWidgets.TextEffectEmitter
import com.fourinachamber.fortyfive.screen.gameWidgets.VerticalStatusEffectDisplay
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.customActor.findAnimationSpawner
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import onj.value.OnjValue
import java.lang.Integer.max
import kotlin.math.sin

data class EnemyPrototype(
    val name: String,
    val baseHealth: Int,
    private val creator: (health: Int) -> Enemy
) {
    fun create(health: Int): Enemy = creator(health)
}

/**
 * represents an enemy
 * @param name the name of the enemy
 * @param health the initial (and maximum) lives of this enemy
 * @param detailFont the font used for the description of the enemy
 * @param detailFontScale scales [detailFont]
 * @param detailFontColor the color of [detailFont]
 */
class Enemy(
    val name: String,
    val drawableHandle: ResourceHandle,
    val coverIconHandle: ResourceHandle,
    val hiddenActionIconHandle: ResourceHandle,
    val health: Int,
    val enemyWidth: Float,
    val enemyHeight: Float,
    val coverIconScale: Float,
    val indicatorIconScale: Float,
    val detailFontHandle: String,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    val detailFontColorDark: Color,
    val headOffset: Float,
    textEmitterConfig: OnjArray,
    private val screen: OnjScreen
) {

    val logTag = "enemy-$name-${++instanceCounter}"

    private var brain: EnemyBrain = NoOpEnemyBrain

    private val gameController = FortyFive.currentGame!!

    /**
     * the current lives of this enemy
     */
    var currentHealth: Int = health
        private set(value) {
            FortyFiveLogger.debug(logTag, "enemy lives updated: new lives = $field ")
            val oldValue = field
            field = max(value, -300)
            if (oldValue > 0 && value <= 0) {
                gameController.enemyDefeated(this)
            }
        }

    val isDefeated: Boolean
        get() = currentHealth <= 0

    var currentCover: Int = 0
        private set(value) {
            field = value
            FortyFiveLogger.debug(logTag, "enemy cover updated: new cover = $field")
        }

    private val _statusEffects: MutableList<StatusEffect> = mutableListOf()

    val statusEffects: List<StatusEffect>
        get() = _statusEffects

    var additionalDamage: Int = 0
        private set
    var additionDamageEffect: StatusEffect? = null
        private set

    fun chooseNewAction(controller: GameController, difficulty: Double, otherActions: List<NextEnemyAction>): NextEnemyAction {
        additionalDamage = 0
        additionDamageEffect = null
        val nextAction = brain.chooseNewAction(controller, this, difficulty, otherActions)
        if (
            nextAction !is NextEnemyAction.ShownEnemyAction ||
            nextAction.action.prototype !is EnemyActionPrototype.DamagePlayer
        ) {
            return nextAction
        }
        val additionalDmgActions = controller
            .playerStatusEffects
            .zip { it.additionalEnemyDamage(nextAction.action.directDamageDealt, StatusEffectTarget.PlayerTarget) }
            .filter { it.second != 0 }
        if (additionalDmgActions.isEmpty()) return nextAction
        if (additionalDmgActions.size > 1) {
            FortyFiveLogger.warn(logTag, "Having more than one status effect that increases enemy damage is currently not supported")
        }
        val (action, additionalDamage) = additionalDmgActions.first()
        this.additionalDamage = additionalDamage
        this.additionDamageEffect = action
        return nextAction
    }

    fun resolveAction(controller: GameController, difficulty: Double): EnemyAction? {
        val action = brain.resolveEnemyAction(controller, this, difficulty)
        return action
    }

    fun onDefeat() {
//        _statusEffects.forEach { actor.removeStatusEffect(it) }
        _statusEffects.clear()
//        actor.setupForAction(NextEnemyAction.None)
    }

    fun applyEffect(effect: StatusEffect) {
        if (isDefeated) return
        FortyFiveLogger.debug(logTag, "status effect $effect applied to enemy")
        for (effectToTest in _statusEffects) if (effectToTest.canStackWith(effect)) {
            FortyFiveLogger.debug(logTag, "stacked with $effectToTest")
            effectToTest.stack(effect)
            return
        }
        effect.start(gameController)
        effect.initIcon(gameController)
        _statusEffects.add(effect)
//        actor.displayStatusEffect(effect)
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
        _statusEffects
            .filter { !it.isStillValid() }
            .forEach {
//                actor.removeStatusEffect(it)
            }
        _statusEffects.removeIf { !it.isStillValid() }
    }

    @MainThreadOnly
    fun damagePlayerDirectly(damage: Int, gameController: GameController): Timeline = Timeline.timeline {
//        val chargeTimeline = GraphicsConfig.chargeTimeline(actor)
//        include(chargeTimeline)
//        delay(GraphicsConfig.bufferTime)
        includeLater(
            { getPlayerDamagedTimeline(damage, gameController) },
            { true }
        )
    }


    private fun getPlayerDamagedTimeline(
        damage: Int,
        gameController: GameController,
    ): Timeline = Timeline.timeline {
        include(gameController.damagePlayerTimeline(damage))
    }

    fun addCoverTimeline(amount: Int): Timeline = Timeline.timeline {
        action {
            currentCover += amount
//            actor.startCoverChangeAnimation(amount)
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
//                    actor.startCoverChangeAnimation(-damage.coerceAtMost(currentCover))
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
//                    actor.updateText()
                }
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                action {
                    currentHealth -= remaining
//                    actor.updateText()
//                    actor.startHealthChangeAnimation(-remaining)
                }
            } },
            { remaining != 0 }
        )

        includeLater(
            { executeStatusEffectsAfterDamage(damage) },
            { remaining != 0 && !triggeredByStatusEffect }
        )
    }

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
            val gameController = FortyFive.currentGame!!
            val curScreen = gameController.screen
            val drawableHandle = onj.get<String>("texture")
            val coverIconHandle = onj.get<String>("coverIcon")
            val detailFont = ResourceManager.forceGet<BitmapFont>(
                object : ResourceBorrower {},
                curScreen,
                onj.get<String>("detailFont")
            )
            val enemy = Enemy(
                onj.get<String>("name"),
                drawableHandle,
                coverIconHandle,
                onj.get<String>("hiddenActionIcon"),
                health,
                onj.get<Double>("width").toFloat(),
                onj.get<Double>("height").toFloat(),
                onj.get<Double>("coverIconScale").toFloat(),
                onj.get<Double>("indicatorIconScale").toFloat(),
                onj.get<String>("detailFont"),
                detailFont,
                onj.get<Double>("detailFontScale").toFloat(),
                onj.get<Color>("detailFontColor"),
                onj.get<Color>("detailFontColorDark"),
                onj.getOr("headOffset", 1.0).toFloat(),
                onj.get<OnjArray>("textEmitterConfig"),
                curScreen
            )
            val brain = EnemyBrain.fromOnj(onj.get<OnjNamedObject>("brain"), enemy)
            enemy.brainTransplant(brain)
            return enemy
        }

    }

}

///**
// * used for representing an enemy on the screen
// */
//class EnemyActor(
//    val enemy: Enemy,
//    textEmitterConfig: OnjArray,
//    private val hiddenActionIconHandle: ResourceHandle,
//    val screen: OnjScreen
//) : ResourceBorrower {
//
//    private val fontColor = if (GraphicsConfig.isEncounterBackgroundDark(MapManager.currentDetailMap.biome)) {
//        enemy.detailFontColorDark
//    } else {
//        enemy.detailFontColor
//    }
//
//    private val enemyDrawable: Promise<Drawable> = ResourceManager.request(this, screen, enemy.drawableHandle)
//
//    private val defeatedDrawable: Promise<Drawable> = GraphicsConfig.defeatedEnemyDrawable(this, screen)
//
//
//    private val attackIndicatorAnimTimeOffset: Int = (0..10_000).random()
//
//    private val enemyActionAnimationTemplateName: String = "enemy_action_animation" // TODO: fix
//    private val enemyActionAnimationParentName: String = "enemy_action_animation_parent" // TODO: fix
//
//    private val animationLifetime: EndableLifetime = EndableLifetime()
//
//    // animations are hardcoded, deal with it
//    private val animation: AnimationDrawable? = when {
//
//        enemy.name.startsWith("Outlaw") || enemy.name.startsWith("tutorial") -> createAnimation(this, animationLifetime.shorter(screen)) {
//            val anim = deferredAnimation("outlaw_animation")
//            order {
//                loop(anim, frameOffset = (0..50).random())
//            }
//        }
//
//        enemy.name.startsWith("Pyro") -> createAnimation(this, animationLifetime.shorter(screen)) {
//            val anim = deferredAnimation("pyro_animation")
//            order {
//                loop(anim, frameOffset = (0..50).random())
//            }
//        }
//
//        else -> null
//
//    }
//
//    init {
//        val emitterConfig = TextEffectEmitter.configsFromOnj(textEmitterConfig, screen)
//        val healthTextEmitter = TextEffectEmitter(emitterConfig, screen)
//        val coverTextEmitter = TextEffectEmitter(emitterConfig, screen)
//
//        animation?.start()
//    }
//
//    fun enemyActionAnimationTimeline(action: EnemyAction, controller: GameController): Timeline = if (action.prototype.hasSpecialAnimation) {
//        specialEnemyActionAnimationTimeline(action, controller)
//    } else {
//        Timeline()
//    }
//
//    private fun specialEnemyActionAnimationTimeline(action: EnemyAction, controller: GameController): Timeline = Timeline.timeline {
//        val actionDescription =
//            TemplateString(action.prototype.descriptionTemplate, action.descriptionParams).string.onjString()
//        val data = mapOf<String, OnjValue>(
//            "commonPanel1" to action.prototype.commonPanel1.onjString(),
//            "commonPanel2" to action.prototype.commonPanel2.onjString(),
//            "commonPanel3" to action.prototype.commonPanel3.onjString(),
//            "actionPanel" to action.prototype.specialPanel.onjString(),
//            "actionName" to action.prototype.title.onjString(),
//            "actionDescription" to actionDescription,
//            "actionIcon" to action.prototype.iconHandle.onjString(),
//        )
//        val parent = screen.namedActorOrError(enemyActionAnimationParentName) as? FlexBox
//            ?: throw RuntimeException("actor named $enemyActionAnimationParentName must be a FlexBox")
//        var animActor: CustomFlexBox? = null
//        action {
//            animActor = screen.screenBuilder.generateFromTemplate(
//                enemyActionAnimationTemplateName,
//                data,
//                parent,
//                screen
//            ) as? CustomFlexBox
//                ?: throw RuntimeException("template named $enemyActionAnimationTemplateName must be a FlexBox")
//        }
//        delay(10)
//        action {
//            screen.enterState("enemy_action_anim")
//            controller.dispatchAnimTimeline(Timeline.timeline {
//                repeat(4) {
//                    action {
//                        SoundPlayer.situation("enemy_action_anim", screen)
//                    }
//                    delay(200)
//                }
//            })
//        }
//        awaitConfirmationInput(screen, maxTime = 10_000)
////        awaitConfirmationInput(screen, maxTime = 5_000)
//        action {
//            screen.leaveState("enemy_action_anim")
//            parent.remove(animActor!!.styleManager!!.node)
//            screen.removeAllStyleManagers(animActor!!)
//        }
//    }
//
//    fun setupForAction(action: NextEnemyAction) {
//
//    }
//
//    fun displayStatusEffect(effect: StatusEffect) = statusEffectDisplay.displayEffect(effect)
//    fun removeStatusEffect(effect: StatusEffect) = statusEffectDisplay.removeEffect(effect)
//
//    fun defeated() {
//        animationLifetime.die()
//    }
//
//    /**
//     * updates the description text of the actor
//     */
//    fun updateText() {
//    }
//
//}

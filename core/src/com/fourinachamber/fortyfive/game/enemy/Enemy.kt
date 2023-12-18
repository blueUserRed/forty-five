package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.gameComponents.StatusEffectDisplay
import com.fourinachamber.fortyfive.screen.gameComponents.TextEffectEmitter
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
 * @param scaleX scales [actor]
 * @param scaleY scales [actor]
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
    val scaleX: Float,
    val scaleY: Float,
    val coverIconScale: Float,
    val indicatorIconScale: Float,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    textEmitterConfig: OnjArray,
    private val screen: OnjScreen
) {

    val logTag = "enemy-$name-${++instanceCounter}"

    var brain: EnemyBrain = NoOpEnemyBrain
        private set

    /**
     * the actor that represents this enemy on the screen
     */
    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    private val gameController = FortyFive.currentGame!!

    /**
     * the current lives of this enemy
     */
    var currentHealth: Int = health
        private set(value) {
            FortyFiveLogger.debug(logTag, "enemy lives updated: new lives = $field ")
            val oldValue = field
            field = value
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
            actor.updateText()
        }

    private val _statusEffects: MutableList<StatusEffect> = mutableListOf()

    val statusEffects: List<StatusEffect>
        get() = _statusEffects

    init {
        actor = EnemyActor(this, textEmitterConfig, hiddenActionIconHandle, screen)
    }

    fun onDefeat() {
        _statusEffects.forEach { actor.removeStatusEffect(it) }
        _statusEffects.clear()
        actor.setupForAction(NextEnemyAction.None)
    }

    fun applyEffect(effect: StatusEffect) {
        FortyFiveLogger.debug(logTag, "status effect $effect applied to enemy")
        for (effectToTest in _statusEffects) if (effectToTest.canStackWith(effect)) {
            FortyFiveLogger.debug(logTag, "stacked with $effectToTest")
            effectToTest.stack(effect)
            return
        }
        effect.start(gameController)
        effect.initIcon(gameController)
        _statusEffects.add(effect)
        actor.displayStatusEffect(effect)
    }

    fun executeStatusEffectsAfterTurn(): Timeline = _statusEffects
        .mapNotNull { it.executeOnNewTurn(StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun executeStatusEffectsAfterDamage(damage: Int): Timeline = _statusEffects
        .mapNotNull { it.executeAfterDamage(damage, StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun executeStatusEffectsAfterRevolverRotation(rotation: GameController.RevolverRotation): Timeline = _statusEffects
        .mapNotNull { it.executeAfterRotation(rotation, StatusEffectTarget.EnemyTarget(this)) }
        .collectTimeline()

    fun update() {
        _statusEffects
            .filter { !it.isStillValid() }
            .forEach {
                actor.removeStatusEffect(it)
            }
        _statusEffects.removeIf { !it.isStillValid() }
    }

    @MainThreadOnly
    fun damagePlayerDirectly(damage: Int, gameController: GameController): Timeline = Timeline.timeline {
        val chargeTimeline = GraphicsConfig.chargeTimeline(actor)
        include(chargeTimeline)
        delay(GraphicsConfig.bufferTime)
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
            actor.startCoverChangeAnimation(amount)
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
                    actor.startCoverChangeAnimation(-damage.coerceAtMost(currentCover))
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
                    actor.updateText()
                }
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                action {
                    currentHealth -= remaining
                    actor.updateText()
                    actor.startHealthChangeAnimation(-remaining)
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
            val curScreen = gameController.curScreen
            val drawableHandle = onj.get<String>("texture")
            val coverIconHandle = onj.get<String>("coverIcon")
            val detailFont = ResourceManager.get<BitmapFont>(curScreen, onj.get<String>("detailFont"))
            val enemy = Enemy(
                onj.get<String>("name"),
                drawableHandle,
                coverIconHandle,
                onj.get<String>("hiddenActionIcon"),
                health,
                onj.get<Double>("scaleX").toFloat(),
                onj.get<Double>("scaleY").toFloat(),
                onj.get<Double>("coverIconScale").toFloat(),
                onj.get<Double>("indicatorIconScale").toFloat(),
                detailFont,
                onj.get<Double>("detailFontScale").toFloat(),
                onj.get<Color>("detailFontColor"),
                onj.get<OnjArray>("textEmitterConfig"),
                curScreen
            )
            val brain = EnemyBrain.fromOnj(onj.get<OnjNamedObject>("brain"), enemy)
            enemy.brainTransplant(brain)
            return enemy
        }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(
    val enemy: Enemy,
    textEmitterConfig: OnjArray,
    private val hiddenActionIconHandle: ResourceHandle,
    override val screen: OnjScreen
) : CustomVerticalGroup(screen), ZIndexActor {

    override var fixedZIndex: Int = 0
    private val image: CustomImageActor = CustomImageActor(enemy.drawableHandle, screen)
    private val coverIcon: CustomImageActor = CustomImageActor(enemy.coverIconHandle, screen)
    val coverText: CustomLabel = CustomLabel(screen, "", Label.LabelStyle(enemy.detailFont, enemy.detailFontColor))
    private var enemyBox = CustomHorizontalGroup(screen)
    private val attackIndicator = CustomHorizontalGroup(screen)
    private val attackIcon = CustomImageActor(null, screen, false)
    private val attackLabel = CustomLabel(screen, "", Label.LabelStyle(enemy.detailFont, enemy.detailFontColor))
    private val statusEffectDisplay = StatusEffectDisplay(
        screen,
        enemy.detailFont,
        enemy.detailFontColor,
        enemy.detailFontScale
    )

    val healthLabel: CustomLabel = CustomLabel(
        screen,
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    private val enemyActionAnimationTemplateName: String = "enemy_action_animation" // TODO: fix
    private val enemyActionAnimationParentName: String = "enemy_action_animation_parent" // TODO: fix

    private val fireParticles: ParticleEffect by lazy {
        ResourceManager.get(screen, "fire_particle") // TODO: fix
    }

    init {
        val emitterConfig = TextEffectEmitter.configsFromOnj(textEmitterConfig, screen)
        val healthTextEmitter = TextEffectEmitter(emitterConfig, screen)
        val coverTextEmitter = TextEffectEmitter(emitterConfig, screen)
        healthLabel.addAnimationSpawner(healthTextEmitter)
        coverText.addAnimationSpawner(coverTextEmitter)
        healthLabel.setFontScale(enemy.detailFontScale)
        coverText.setFontScale(enemy.detailFontScale)
        attackLabel.setFontScale(enemy.detailFontScale)

        // When multiple enemies are in an encounter, the last attackIndicator will override the previous ones,
        // but because the name is only used for the tutorial, this should not be an issue
        screen.addNamedActor("attackIndicator", attackIcon)

        image.setScale(enemy.scaleX, enemy.scaleY)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        coverIcon.setScale(enemy.coverIconScale)
        attackIcon.setScale(enemy.indicatorIconScale) // TODO: fix
        coverIcon.reportDimensionsWithScaling = true
        coverIcon.ignoreScalingWhenDrawing = true
        attackIcon.reportDimensionsWithScaling = true
        attackIcon.ignoreScalingWhenDrawing = true

        attackIndicator.addActor(attackIcon)
        attackIndicator.addActor(attackLabel)
        attackIndicator.isVisible = false

        val coverInfoBox = CustomVerticalGroup(screen)
        coverInfoBox.addActor(coverIcon)
        coverInfoBox.addActor(coverText)

        enemyBox.addActor(coverInfoBox)
        enemyBox.addActor(image)

        addActor(attackIndicator)
        addActor(enemyBox)
        addActor(healthLabel)
        addActor(statusEffectDisplay)
        updateText()
    }

    fun startHealthChangeAnimation(change: Int) {
       val emitter = healthLabel.findAnimationSpawner<TextEffectEmitter>() ?: return
        emitter.playNumberChangeAnimation(change)
    }

    // TODO: function for adding to Cover
    fun startCoverChangeAnimation(change: Int) {
        val emitter = coverText.findAnimationSpawner<TextEffectEmitter>() ?: return
        emitter.playNumberChangeAnimation(change)
    }

    fun enemyActionAnimationTimeline(action: EnemyAction): Timeline = if (action.prototype.hasSpecialAnimation) {
        specialEnemyActionAnimationTimeline(action)
    } else {
        Timeline()
    }

    private fun specialEnemyActionAnimationTimeline(action: EnemyAction): Timeline = Timeline.timeline {
        val actionDescription =
            TemplateString(action.prototype.descriptionTemplate, action.descriptionParams).string.onjString()
        val data = mapOf<String, OnjValue>(
            "commonPanel1" to action.prototype.commonPanel1.onjString(),
            "commonPanel2" to action.prototype.commonPanel2.onjString(),
            "commonPanel3" to action.prototype.commonPanel3.onjString(),
            "actionPanel" to action.prototype.specialPanel.onjString(),
            "actionName" to action.prototype.title.onjString(),
            "actionDescription" to actionDescription,
            "actionIcon" to action.prototype.iconHandle.onjString(),
        )
        val parent = screen.namedActorOrError(enemyActionAnimationParentName) as? FlexBox
            ?: throw RuntimeException("actor named $enemyActionAnimationParentName must be a FlexBox")
        var animActor: CustomFlexBox? = null
        action {
            animActor = screen.screenBuilder.generateFromTemplate(
                enemyActionAnimationTemplateName,
                data,
                parent,
                screen
            ) as? CustomFlexBox
                ?: throw RuntimeException("template named $enemyActionAnimationTemplateName must be a FlexBox")
        }
        delay(10)
        action {
            screen.enterState("enemy_action_anim")
        }
        awaitConfirmationInput(screen, maxTime = 10_000)
//        awaitConfirmationInput(screen, maxTime = 5_000)
        action {
            screen.leaveState("enemy_action_anim")
            parent.remove(animActor!!.styleManager!!.node)
        }
    }

    fun setupForAction(action: NextEnemyAction) = when (action) {

        is NextEnemyAction.None -> {
            attackIndicator.isVisible = false
        }

        is NextEnemyAction.ShownEnemyAction -> {
            attackIcon.backgroundHandle = action.action.prototype.iconHandle
            attackLabel.setText(action.action.indicatorText)
            attackIndicator.isVisible = true
        }

        is NextEnemyAction.HiddenEnemyAction -> {
            attackIcon.backgroundHandle = hiddenActionIconHandle
            attackIndicator.isVisible = true
        }

    }

    fun fireAnim(): Timeline = Timeline.timeline {
        includeAction(ParticleTimelineAction(
            fireParticles,
            image.localToStageCoordinates(Vector2(0f, 0f)) + Vector2(image.width / 2, 0f),
            screen
        ))
    }

    fun displayStatusEffect(effect: StatusEffect) = statusEffectDisplay.displayEffect(effect)
    fun removeStatusEffect(effect: StatusEffect) = statusEffectDisplay.removeEffect(effect)

    /**
     * updates the description text of the actor
     */
    fun updateText() {
        coverText.setText("${enemy.currentCover}")
        healthLabel.setText("${enemy.currentHealth}/${enemy.health}")
    }

}

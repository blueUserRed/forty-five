package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.gameComponents.StatusEffectDisplay
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.max
import java.lang.Integer.min

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
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    val actionProbability: Float,
    private val screen: OnjScreen
) {

    val logTag = "enemy-$name-${++instanceCounter}"

    /**
     * the actor that represents this enemy on the screen
     */
    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    private val gameController = FortyFive.currentGame!!

    private val _actions: MutableList<Pair<Int, EnemyActionPrototype>> = mutableListOf()

    val actionPrototypes: List<Pair<Int, EnemyActionPrototype>>
        get() = _actions

    /**
     * the current lives of this enemy
     */
    var currentHealth: Int = health
        private set(value) {
            FortyFiveLogger.debug(logTag, "enemy lives updated: new lives = $field ")
            if (field > 0 && value <= 0) {
                gameController.enemyDefeated(this)
            }
            field = value
        }

    val isDefeated: Boolean
        get() = currentHealth <= 0

    var currentCover: Int = 0
        set(value) {
            field = value
            FortyFiveLogger.debug(logTag, "enemy cover updated: new cover = $field")
            actor.updateText()
        }

    private val _statusEffects: MutableList<StatusEffect> = mutableListOf()

    val statusEffect: List<StatusEffect>
        get() = _statusEffects

    init {
        actor = EnemyActor(this, hiddenActionIconHandle, screen)
    }

    fun addEnemyAction(weight: Int, action: EnemyActionPrototype) {
        _actions.add(weight to action)
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
    ): Timeline {

//        val shakeAction = GraphicsConfig.shakeActorAnimation(livesLabel, false)

//        val textAnimation = GraphicsConfig.numberChangeAnimation(
//            livesLabel.localToStageCoordinates(Vector2(0f, 0f)),
//            "-$damage",
//            false,
//            false,
//            gameController.curScreen
//        )

        return Timeline.timeline {
            include(gameController.damagePlayerTimeline(damage))
//            parallelActions(shakeAction, textAnimation)
        }
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
                val anim = GraphicsConfig.numberChangeAnimation(
                    actor.coverText.localToStageCoordinates(Vector2(0f, 0f)),
                    "-${min(damage, currentCover)}",
                    false,
                    false,
                    gameController.curScreen
                )
                action {
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
                    actor.updateText()
                    gameController.dispatchAnimTimeline(anim.wrap())
                }
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                val anim = GraphicsConfig.numberChangeAnimation(
                    actor.healthLabel.localToStageCoordinates(Vector2(0f, 0f)),
                    "-$remaining",
                    false,
                    false,
                    gameController.curScreen
                )
                action {
                    currentHealth -= remaining
                    actor.updateText()
                    gameController.dispatchAnimTimeline(anim.wrap())
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
                detailFont,
                onj.get<Double>("detailFontScale").toFloat(),
                onj.get<Color>("detailFontColor"),
                onj.get<Double>("actionProbability").toFloat(),
                curScreen
            )
            onj
                .get<OnjArray>("actions")
                .value
                .map { it as OnjNamedObject }
                .map { it.get<Long>("weight").toInt() to EnemyActionPrototype.fromOnj(it, enemy) }
                .forEach { (weight, action) -> enemy.addEnemyAction(weight, action) }
            return enemy
        }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(
    val enemy: Enemy,
    private val hiddenActionIconHandle: ResourceHandle,
    private val screen: OnjScreen
) : CustomVerticalGroup(screen), ZIndexActor, AnimationActor {

    override var inAnimation: Boolean = false
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

    private val fireParticles: ParticleEffect by lazy {
        ResourceManager.get(screen, "fire_particle") // TODO: fix
    }

    init {
        healthLabel.setFontScale(enemy.detailFontScale)
        coverText.setFontScale(enemy.detailFontScale)
        attackLabel.setFontScale(enemy.detailFontScale)

        image.setScale(enemy.scaleX, enemy.scaleY)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        coverIcon.setScale(enemy.coverIconScale)
        attackIcon.setScale(enemy.coverIconScale) // TODO: fix
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

    fun setupForAction(action: GameDirector.NextAction) = when (action) {

        is GameDirector.NextAction.None -> {
            attackIndicator.isVisible = false
        }

        is GameDirector.NextAction.ShownEnemyAction -> {
            attackIcon.backgroundHandle = action.action.iconHandle
                ?: throw RuntimeException("action ${action.action.prototype} can be shown but defines no icon")
            attackLabel.setText(action.action.indicatorText)
            attackIndicator.isVisible = true
        }

        is GameDirector.NextAction.HiddenEnemyAction -> {
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

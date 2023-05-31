package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.gameComponents.EnemyArea
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.gameComponents.CoverStack
import com.fourinachamber.fortyfive.screen.gameComponents.StatusEffectDisplay
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.onClick
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.Integer.max
import java.lang.Integer.min

/**
 * represents an enemy
 * @param name the name of the enemy
 * @param drawable the texture of this enemy
 * @param offsetX x-offset from the origin of [EnemyArea] to the point where this enemy is located
 * @param offsetY y-offset from the origin of [EnemyArea] to the point where this enemy is located
 * @param scaleX scales [actor]
 * @param scaleY scales [actor]
 * @param lives the initial (and maximum) lives of this enemy
 * @param detailFont the font used for the description of the enemy
 * @param detailFontScale scales [detailFont]
 * @param detailFontColor the color of [detailFont]
 */
class Enemy(
    val name: String,
    val drawableHandle: ResourceHandle,
    val coverIconHandle: ResourceHandle,
    val lives: Int,
    val offsetX: Float,
    val offsetY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val coverIconScale: Float,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    val area: EnemyArea,
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

    /**
     * the current lives of this enemy
     */
    var currentLives: Int = lives
        private set(value) {
            field = max(value, 0)
            FortyFiveLogger.debug(logTag, "enemy lives updated: new lives = $field ")
            if (field != 0) return
            gameController.curScreen.afterMs(10) { //TODO: nooooo, not again
                gameController.executeTimelineLater(Timeline.timeline {
                    mainThreadAction { gameController.enemyDefeated(this@Enemy) }
                })
            }
        }

    var currentCover: Int = 0
        set(value) {
            field = value
            FortyFiveLogger.debug(logTag, "enemy cover updated: new cover = $field")
            actor.updateText()
        }

    var curAction: EnemyAction? = null
        private set

    private val statusEffects: MutableList<StatusEffect> = mutableListOf()

    private lateinit var brain: EnemyBrain

    init {
        actor = EnemyActor(this, area, screen)
    }

    fun applyEffect(effect: StatusEffect) {
        FortyFiveLogger.debug(logTag, "status effect $effect applied to enemy")
        for (effectToTest in statusEffects) if (effectToTest.canStackWith(effect)) {
            FortyFiveLogger.debug(logTag, "stacked with $effectToTest")
            effectToTest.stack(effect)
            return
        }
        effect.start(gameController)
        effect.initIcon(gameController)
        statusEffects.add(effect)
        actor.displayStatusEffect(effect)
    }

    fun executeStatusEffects(): Timeline? {
        var hadEffectTimeline = false
        val timeline = Timeline.timeline {
            for (effect in statusEffects) {
                val timelineToInclude = effect.executeAfterRound(gameController) ?: continue
                hadEffectTimeline = true
                include(timelineToInclude)
            }
        }
        return if (hadEffectTimeline) timeline else null
    }

    fun executeStatusEffectsAfterDamage(damage: Int): Timeline? {
        var hadEffectTimeline = false
        val timeline = Timeline.timeline {
            for (effect in statusEffects) {
                val timelineToInclude = effect.executeAfterDamage(gameController, damage) ?: continue
                hadEffectTimeline = true
                include(timelineToInclude)
            }
        }
        return if (hadEffectTimeline) timeline else null
    }

    fun executeStatusEffectsAfterRevolverTurn(): Timeline? {
        var hadEffectTimeline = false
        val timeline = Timeline.timeline {
            for (effect in statusEffects) {
                val timelineToInclude = effect.executeAfterRevolverTurn(gameController) ?: continue
                hadEffectTimeline = true
                include(timelineToInclude)
            }
        }
        return if (hadEffectTimeline) timeline else null
    }

    fun onRevolverTurn() {
        val iterator = statusEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.onRevolverTurn(gameController)
            if (!effect.isStillValid()) {
                FortyFiveLogger.debug(logTag, "status effect $effect no longer valid")
                actor.removeStatusEffect(effect)
                iterator.remove()
            }
        }
        actor.onRevolverTurn()
    }

    fun chooseNewAction() {
        curAction = brain.chooseAction()
        actor.displayAction(curAction!!)
        FortyFiveLogger.debug(logTag, "chose new action: $curAction")
    }

    @MainThreadOnly
    fun doAction(): Timeline? {
        return curAction!!.execute()
    }

    fun resetAction() {
        curAction = null
        actor.resetAction()
    }

    @MainThreadOnly
    fun damagePlayer(damage: Int, gameController: GameController): Timeline = Timeline.timeline {
        val screen = gameController.curScreen
        val chargeTimeline = GraphicsConfig.chargeTimeline(actor)

        val overlayAction = GraphicsConfig.damageOverlay(screen)

        var activeStack: CoverStack? = null
        var remaining = 0

        include(chargeTimeline)

        action {
            remaining = gameController.coverArea.damage(damage)
            if (remaining != damage) activeStack = gameController.coverArea.getActive()
        }

        includeLater(
            { Timeline.timeline { includeAction(
                GraphicsConfig.coverStackParticles(activeStack!!.currentHealth == 0, activeStack!!, screen)
            ) } },
            { activeStack != null }

        )

        includeActionLater(overlayAction) { remaining != 0 }
        delay(GraphicsConfig.bufferTime)
        includeLater(
            { getPlayerDamagedTimeline(remaining, gameController) },
            { remaining != 0 }
        )
    }


    private fun getPlayerDamagedTimeline(
        damage: Int,
        gameController: GameController,
    ): Timeline {

        val livesLabel = gameController.playerLivesLabel
        val shakeAction = GraphicsConfig.shakeActorAnimation(livesLabel, false)

        val textAnimation = GraphicsConfig.numberChangeAnimation(
            livesLabel.localToStageCoordinates(Vector2(0f, 0f)),
            "-$damage",
            false,
            false,
            gameController.curScreen
        )

        return Timeline.timeline {
            action { gameController.damagePlayer(damage) }
            parallelActions(shakeAction, textAnimation)
        }
    }


    /**
     * reduces the enemies lives by [damage]
     */
    fun damage(damage: Int): Timeline = Timeline.timeline {
        var remaining = 0

        action {
            remaining = max(damage - currentCover, 0)
        }

        includeLater(
            { Timeline.timeline {
                action {
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
                    actor.updateText()
                }
                includeAction(GraphicsConfig.numberChangeAnimation(
                    actor.coverText.localToStageCoordinates(Vector2(0f, 0f)),
                    "-${min(damage, currentCover)}",
                    false,
                    false,
                    gameController.curScreen
                ))
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                action {
                    currentLives -= remaining
                    actor.updateText()
                }
                includeAction(GraphicsConfig.numberChangeAnimation(
                    actor.livesLabel.localToStageCoordinates(Vector2(0f, 0f)),
                    "-$remaining",
                    false,
                    false,
                    gameController.curScreen
                ))
            } },
            { remaining != 0 }
        )
    }

    companion object {

        private var instanceCounter = 0

        /**
         * reads an array of Enemies from on an OnjArray
         */
        @MainThreadOnly
        fun getFrom(
            enemiesOnj: OnjArray,
            area: EnemyArea,
            screen: OnjScreen
        ): List<Enemy> = enemiesOnj
            .value
            .map {
                it as OnjObject
                val gameController = FortyFive.currentGame!!
                val curScreen = gameController.curScreen
                val drawableHandle = it.get<String>("texture")
                val coverIconHandle = it.get<String>("coverIcon")
                val detailFont = ResourceManager.get<BitmapFont>(curScreen, it.get<String>("detailFont"))
                val enemy = Enemy(
                    it.get<String>("name"),
                    drawableHandle,
                    coverIconHandle,
                    it.get<Long>("lives").toInt(),
                    it.get<Double>("offsetX").toFloat(),
                    it.get<Double>("offsetY").toFloat(),
                    it.get<Double>("scaleX").toFloat(),
                    it.get<Double>("scaleY").toFloat(),
                    it.get<Double>("coverIconScale").toFloat(),
                    detailFont,
                    it.get<Double>("detailFontScale").toFloat(),
                    it.get<Color>("detailFontColor"),
                    area,
                    screen
                )
                enemy.brain = EnemyBrain.fromOnj(it.get<OnjObject>("brain"), curScreen, enemy)
                enemy
            }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(
    val enemy: Enemy,
    private val area: EnemyArea,
    private val screen: OnjScreen
) : CustomVerticalGroup(screen), ZIndexActor, AnimationActor {

    override var inAnimation: Boolean = false
    override var fixedZIndex: Int = 0
    private val image: CustomImageActor = CustomImageActor(enemy.drawableHandle, screen)
    private val coverIcon: CustomImageActor = CustomImageActor(enemy.coverIconHandle, screen)
    val coverText: CustomLabel = CustomLabel(screen, "", Label.LabelStyle(enemy.detailFont, enemy.detailFontColor))
    private var enemyBox = CustomHorizontalGroup(screen)
    private val actionIndicator: CustomHorizontalGroup = CustomHorizontalGroup(screen)
    private val statusEffectDisplay = StatusEffectDisplay(
        screen,
        enemy.detailFont,
        enemy.detailFontColor,
        enemy.detailFontScale
    )

    private val actionIndicatorText: CustomLabel = CustomLabel(
        screen,
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    val livesLabel: CustomLabel = CustomLabel(
        screen,
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    init {
        livesLabel.setFontScale(enemy.detailFontScale)
        coverText.setFontScale(enemy.detailFontScale)
        actionIndicatorText.setFontScale(enemy.detailFontScale)
        actionIndicator.addActor(actionIndicatorText)
        image.setScale(enemy.scaleX, enemy.scaleY)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        coverIcon.setScale(enemy.coverIconScale)
        coverIcon.reportDimensionsWithScaling = true
        coverIcon.ignoreScalingWhenDrawing = true

        addActor(actionIndicator)

        val coverInfoBox = CustomVerticalGroup(screen)
        coverInfoBox.addActor(coverIcon)
        coverInfoBox.addActor(coverText)

        enemyBox.addActor(coverInfoBox)
        enemyBox.addActor(image)

        addActor(enemyBox)
        addActor(livesLabel)
        addActor(statusEffectDisplay)
        updateText()

        onClick { area.selectedEnemy = enemy }
    }

    fun displayAction(action: EnemyAction) {
        val image = CustomImageActor(action.indicatorDrawableHandle, screen)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        image.setScale(action.indicatorScale)
        actionIndicatorText.setText(action.descriptionText)
        actionIndicator.addActorAt(0, image)
    }

    fun resetAction() {
        actionIndicatorText.setText("")
        actionIndicator.removeActorAt(0, true)
    }

    fun displayStatusEffect(effect: StatusEffect) = statusEffectDisplay.displayEffect(effect)
    fun removeStatusEffect(effect: StatusEffect) = statusEffectDisplay.removeEffect(effect)
    fun onRevolverTurn() = statusEffectDisplay.updateRemainingTurns()

    /**
     * updates the description text of the actor
     */
    fun updateText() {
        coverText.setText("${enemy.currentCover}")
        livesLabel.setText("${enemy.currentLives}/${enemy.lives}")
    }

}

package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.StatusEffect
import com.fourinachamber.fourtyfive.game.TextAnimation
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import onj.OnjArray
import onj.OnjObject
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.properties.Delegates

/**
 * represents an enemy
 * @param name the name of the enemy
 * @param texture the texture of this enemy
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
    val texture: TextureRegion,
    val coverIcon: TextureRegion,
    val lives: Int,
    val offsetX: Float,
    val offsetY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val coverIconScale: Float,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    private val gameScreenController: GameScreenController
) {

    /**
     * the actor that represents this enemy on the screen
     */
    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    /**
     * the current lives of this enemy
     */
    var currentLives: Int = lives
        private set

    var currentCover: Int = 0
        set(value) {
            field = value
            actor.updateText()
        }

    var curAction: EnemyAction? = null
        private set

    private val statusEffects: MutableList<StatusEffect> = mutableListOf()

    private lateinit var brain: EnemyBrain

    init {
        actor = EnemyActor(this)
    }

    fun applyEffect(effect: StatusEffect) {
        effect.initIcon(gameScreenController)
        statusEffects.add(effect)
        actor.displayStatusEffect(effect)
    }

    fun executeStatusEffects(gameScreenController: GameScreenController): Timeline? {
        var hadEffectTimeline = false
        val timeline = Timeline.timeline {
            for (effect in statusEffects) {
                val timelineToInclude = effect.execute(gameScreenController)
                if (timelineToInclude != null) {
                    hadEffectTimeline = true
                    include(timelineToInclude)
                }
            }
        }
        return if (hadEffectTimeline) timeline else null
    }

    fun executeStatusEffectsAfterDamage(gameScreenController: GameScreenController, damage: Int): Timeline? {
        var hadEffectTimeline = false
        val timeline = Timeline.timeline {
            for (effect in statusEffects) {
                val timelineToInclude = effect.executeAfterDamage(gameScreenController, damage)
                if (timelineToInclude != null) {
                    hadEffectTimeline = true
                    include(timelineToInclude)
                }
            }
        }
        return if (hadEffectTimeline) timeline else null
    }

    fun onRevolverTurn() {
        val iterator = statusEffects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.onRevolverTurn()
            if (!effect.isStillValid()) {
                actor.removeStatusEffect(effect)
                iterator.remove()
            }
        }
        actor.onRevolverTurn()
    }

    fun chooseNewAction() {
        curAction = brain.chooseAction()
        actor.displayAction(curAction!!)
    }

    fun doAction(gameScreenController: GameScreenController): Timeline {
        return curAction!!.execute(gameScreenController)
    }

    fun resetAction() {
        curAction = null
        actor.resetAction()
    }

    /**
     * reduces the enemies lives by [damage]
     */
    fun damage(damage: Int, gameScreenController: GameScreenController): Timeline = Timeline.timeline {
        val (livesX, livesY) = actor.livesLabel.localToStageCoordinates(Vector2(0f, 0f))

        val livesTextAnimation = TextAnimation(
            livesX, livesY,
            "-$damage",
            dmgFontColor,
            dmgFontScale,
            gameScreenController.curScreen!!.fonts[dmgFontName] ?:
                throw RuntimeException("unknown font $dmgFontName"),
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameScreenController.curScreen!!,
            dmgDuration
        )

        val (coverX, coverY) = actor.coverText.localToStageCoordinates(Vector2(0f, 0f))

        val coverTextAnimation = TextAnimation(
            coverX, coverY,
            "-$damage",
            dmgFontColor,
            dmgFontScale,
            gameScreenController.curScreen!!.fonts[dmgFontName] ?:
                throw RuntimeException("unknown font $dmgFontName"),
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameScreenController.curScreen!!,
            dmgDuration
        )

        var remaining = 0

        action {
            remaining = max(damage - currentCover, 0)
        }

        includeLater(
            { Timeline.timeline {
                action {
                    coverTextAnimation.text = "-${min(damage, currentCover)}"
                    currentCover -= damage
                    if (currentCover < 0) currentCover = 0
                    actor.updateText()
                    gameScreenController.playGameAnimation(coverTextAnimation)
                }
                delayUntil { coverTextAnimation.isFinished() }
            } },
            { currentCover != 0 }
        )

        includeLater(
            { Timeline.timeline {
                action {
                    livesTextAnimation.text = "-$remaining"
                    currentLives -= remaining
                    actor.updateText()
                    gameScreenController.playGameAnimation(livesTextAnimation)
                }
                delayUntil { livesTextAnimation.isFinished() }
            } },
            { remaining != 0 }
        )
    }

    companion object {

        private lateinit var dmgFontName: String
        private lateinit var dmgFontColor: Color
        private var dmgFontScale by Delegates.notNull<Float>()
        private var dmgDuration by Delegates.notNull<Int>()
        private var dmgRaiseHeight by Delegates.notNull<Float>()
        private var dmgStartFadeoutAt by Delegates.notNull<Int>()


        fun init(config: OnjObject) {
            val dmgOnj = config.get<OnjObject>("playerLivesAnimation")

            dmgFontName = dmgOnj.get<String>("font")
            dmgFontScale = dmgOnj.get<Double>("fontScale").toFloat()
            dmgDuration = (dmgOnj.get<Double>("duration") * 1000).toInt()
            dmgRaiseHeight = dmgOnj.get<Double>("raiseHeight").toFloat()
            dmgStartFadeoutAt = (dmgOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            dmgFontColor = Color.valueOf(dmgOnj.get<String>("negativeFontColor"))
        }

        /**
         * reads an array of Enemies from on an OnjArray
         */
        fun getFrom(
            enemiesOnj: OnjArray,
            gameScreenController: GameScreenController
        ): List<Enemy> = enemiesOnj
            .value
            .map {
                it as OnjObject
                val screenDataProvider = gameScreenController.curScreen!!
                val texture = screenDataProvider.textures[it.get<String>("texture")] ?:
                    throw RuntimeException("unknown texture ${it.get<String>("texture")}")
                val coverIcon = screenDataProvider.textures[it.get<String>("coverIcon")] ?:
                    throw RuntimeException("unknown texture ${it.get<String>("coverIcon")}")
                val detailFont = screenDataProvider.fonts[it.get<String>("detailFont")] ?:
                    throw RuntimeException("unknown font ${it.get<String>("detailFont")}")
                val enemy = Enemy(
                    it.get<String>("name"),
                    texture,
                    coverIcon,
                    it.get<Long>("lives").toInt(),
                    it.get<Double>("offsetX").toFloat(),
                    it.get<Double>("offsetY").toFloat(),
                    it.get<Double>("scaleX").toFloat(),
                    it.get<Double>("scaleY").toFloat(),
                    it.get<Double>("coverIconScale").toFloat(),
                    detailFont,
                    it.get<Double>("detailFontScale").toFloat(),
                    Color.valueOf(it.get<String>("detailFontColor")),
                    gameScreenController
                )
                enemy.brain = EnemyBrain.fromOnj(it.get<OnjObject>("brain"), screenDataProvider, enemy)
                enemy
            }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(val enemy: Enemy) : CustomVerticalGroup(), ZIndexActor, AnimationActor {

    override var inAnimation: Boolean = false
    override var fixedZIndex: Int = 0
    private val image: CustomImageActor = CustomImageActor(enemy.texture)
    private val coverIcon: CustomImageActor = CustomImageActor(enemy.coverIcon)
    val coverText: CustomLabel = CustomLabel("", Label.LabelStyle(enemy.detailFont, enemy.detailFontColor))
    private var enemyBox = CustomHorizontalGroup()
    private val actionIndicator: CustomHorizontalGroup = CustomHorizontalGroup()
    private val statusEffectDisplay = StatusEffectDisplay(
        enemy.detailFont,
        enemy.detailFontColor,
        enemy.detailFontScale
    )

    private val actionIndicatorText: CustomLabel = CustomLabel(
        "",
        Label.LabelStyle(enemy.detailFont, enemy.detailFontColor)
    )

    val livesLabel: CustomLabel = CustomLabel(
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

        val coverInfoBox = CustomVerticalGroup()
        coverInfoBox.addActor(coverIcon)
        coverInfoBox.addActor(coverText)

        enemyBox.addActor(coverInfoBox)
        enemyBox.addActor(image)

        addActor(enemyBox)
        addActor(livesLabel)
        addActor(statusEffectDisplay)
        updateText()
    }

    fun displayAction(action: EnemyAction) {
        val image = CustomImageActor(action.indicatorTexture)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        image.setScale(action.indicatorTextureScale)
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

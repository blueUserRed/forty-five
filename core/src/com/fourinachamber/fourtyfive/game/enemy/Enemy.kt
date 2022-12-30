package com.fourinachamber.fourtyfive.game.enemy

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.*
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
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
    val detailFontColor: Color
) {

    val logTag = "enemy-$name-${++instanceCounter}"

    /**
     * the actor that represents this enemy on the screen
     */
    // it's a bit hacky but actor needs to be initialised after currentLives, so the text of actor is correct
    @Suppress("JoinDeclarationAndAssignment")
    val actor: EnemyActor

    private val gameController = FourtyFive.currentGame!!

    /**
     * the current lives of this enemy
     */
    var currentLives: Int = lives
        private set(value) {
            field = max(value, 0)
            FourtyFiveLogger.debug(logTag, "enemy lives updated: new lives = $field ")
            if (field != 0) return
            gameController.curScreen.afterMs(10) { //TODO: nooooo, not again
                gameController.executeTimelineLater(Timeline.timeline {
                    action { gameController.enemyDefeated(this@Enemy) }
                })
            }
        }

    var currentCover: Int = 0
        set(value) {
            field = value
            FourtyFiveLogger.debug(logTag, "enemy cover updated: new cover = $field")
            actor.updateText()
        }

    var curAction: EnemyAction? = null
        private set

    private val statusEffects: MutableList<StatusEffect> = mutableListOf()

    private lateinit var brain: EnemyBrain

    private val dmgFont: BitmapFont = gameController.curScreen!!.fonts[dmgFontName]
        ?: throw RuntimeException("unknown font $dmgFontName")

    private val coverStackDamagedParticles: ParticleEffect =
        gameController.curScreen!!.particles[coverStackDamagedParticlesName]
            ?: throw RuntimeException("unknown particle: $coverStackDamagedParticlesName")

    private val coverStackDestroyedParticles: ParticleEffect =
        gameController.curScreen!!.particles[coverStackDestroyedParticlesName]
            ?: throw RuntimeException("unknown particle: $coverStackDestroyedParticlesName")

    init {
        actor = EnemyActor(this)
    }

    fun applyEffect(effect: StatusEffect) {
        FourtyFiveLogger.debug(logTag, "status effect $effect applied to enemy")
        for (effectToTest in statusEffects) if (effectToTest.canStackWith(effect)) {
            FourtyFiveLogger.debug(logTag, "stacked with $effectToTest")
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
                FourtyFiveLogger.debug(logTag, "status effect $effect no longer valid")
                actor.removeStatusEffect(effect)
                iterator.remove()
            }
        }
        actor.onRevolverTurn()
    }

    fun chooseNewAction() {
        curAction = brain.chooseAction()
        actor.displayAction(curAction!!)
        FourtyFiveLogger.debug(logTag, "chose new action: $curAction")
    }

    fun doAction(): Timeline? {
        return curAction!!.execute()
    }

    fun resetAction() {
        curAction = null
        actor.resetAction()
    }

    fun damagePlayer(damage: Int): Timeline = Timeline.timeline {
        val gameController = FourtyFive.currentGame!!
        val shakeAction = ShakeActorAction(xShake, yShake, xSpeedMultiplier, ySpeedMultiplier)
        shakeAction.duration = shakeDuration

        val moveByAction = CustomMoveByAction()
        moveByAction.setAmount(xCharge, yCharge)
        moveByAction.duration = chargeDuration
        moveByAction.interpolation = chargeInterpolation

        val playerLivesLabel = gameController.playerLivesLabel
        var playerLivesPos = playerLivesLabel.localToStageCoordinates(Vector2(0f, 0f))
        playerLivesPos += Vector2(playerLivesLabel.width / 2f, -playerLivesLabel.height)

        val textAnimation = TextAnimation(
            playerLivesPos.x,
            playerLivesPos.y,
            "If you see this something went wrong",
            dmgFontColor,
            dmgFontScale,
            dmgFont,
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameController.curScreen!!,
            dmgDuration
        )

        val screenDataProvider = gameController.curScreen!!
        val overlayActor = CustomImageActor(screenDataProvider.textures["hit_overlay"]!!)
        val viewport = screenDataProvider.stage.viewport

        //TODO: put these magic numbers in an onj file somewhere
        val overlayAnim = FadeInAndOutAnimation(
            0f, 0f,
            overlayActor,
            screenDataProvider,
            1500, 0, 500,
            Vector2(viewport.worldWidth, viewport.worldHeight)
        )

        var activeStack: CoverStack? = null
        var remaining = 0

        action { actor.addAction(moveByAction) }

        delayUntil { moveByAction.isComplete }

        action {
            actor.removeAction(moveByAction)
            moveByAction.reset()
            moveByAction.amountX = -moveByAction.amountX
            moveByAction.amountY = -moveByAction.amountY
            actor.addAction(moveByAction)
        }

        delayUntil { moveByAction.isComplete }

        action { actor.removeAction(moveByAction) }

        action {
            remaining = gameController.coverArea!!.damage(damage)
            if (remaining != damage) activeStack = gameController.coverArea!!.getActive()
        }

        includeLater(
            {
                getStackParticlesTimeline(activeStack!!, screenDataProvider, activeStack!!.currentHealth == 0)
            },
            { activeStack != null }
        )

        includeLater(
            { Timeline.timeline {
                action { gameController.playGameAnimation(overlayAnim) }
            } },
            { remaining != 0 }
        )

        delay(bufferTime)

        includeLater(
            { getPlayerDamagedTimeline(remaining, shakeAction, gameController, textAnimation) },
            { remaining != 0 }
        )
    }


    private fun getPlayerDamagedTimeline(
        damage: Int,
        shakeAction: ShakeActorAction,
        gameController: GameController,
        textAnimation: TextAnimation
    ): Timeline {

        val playerLivesLabel = gameController.playerLivesLabel!!

        return Timeline.timeline {

            action {
                gameController.damagePlayer(damage)
                playerLivesLabel.addAction(shakeAction)
                textAnimation.text = "-$damage"
                gameController.playGameAnimation(textAnimation)
            }

            delayUntil { textAnimation.isFinished() }

        }
    }

    private fun getStackParticlesTimeline(
        coverStack: CoverStack,
        screenDataProvider: ScreenDataProvider,
        wasDestroyed: Boolean
    ): Timeline = Timeline.timeline {

        var particle: ParticleEffect? = null

        delay(bufferTime)

        action {
            particle = if (wasDestroyed) coverStackDestroyedParticles else coverStackDamagedParticles

            val particleActor = CustomParticleActor(particle!!)
            particleActor.isAutoRemove = true
            particleActor.fixedZIndex = Int.MAX_VALUE

            if (wasDestroyed) {
                particleActor.setPosition(
                    coverStack.x + coverStack.width / 2,
                    coverStack.y + coverStack.height / 2
                )
            } else {
                val width = particle!!.emitters[0].spawnWidth.highMax
                particleActor.setPosition(
                    coverStack.x + coverStack.width / 2 - width / 2,
                    coverStack.y
                )
            }

            screenDataProvider.addActorToRoot(particleActor)
            particleActor.start()
        }

        delayUntil { particle?.isComplete ?: true }

    }


    /**
     * reduces the enemies lives by [damage]
     */
    fun damage(damage: Int): Timeline = Timeline.timeline {
        val gameController = FourtyFive.currentGame!!
        val (livesX, livesY) = actor.livesLabel.localToStageCoordinates(Vector2(0f, 0f))

        val livesTextAnimation = TextAnimation(
            livesX, livesY,
            "-$damage",
            dmgFontColor,
            dmgFontScale,
            gameController.curScreen.fonts[dmgFontName] ?:
                throw RuntimeException("unknown font $dmgFontName"),
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameController.curScreen,
            dmgDuration
        )

        val (coverX, coverY) = actor.coverText.localToStageCoordinates(Vector2(0f, 0f))

        val coverTextAnimation = TextAnimation(
            coverX, coverY,
            "-$damage",
            dmgFontColor,
            dmgFontScale,
            gameController.curScreen.fonts[dmgFontName] ?:
                throw RuntimeException("unknown font $dmgFontName"),
            dmgRaiseHeight,
            dmgStartFadeoutAt,
            gameController.curScreen,
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
                    gameController.playGameAnimation(coverTextAnimation)
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
                    gameController.playGameAnimation(livesTextAnimation)
                }
                delayUntil { livesTextAnimation.isFinished() }
            } },
            { remaining != 0 }
        )
    }

    companion object {

        private var instanceCounter = 0

        private lateinit var dmgFontName: String
        private lateinit var dmgFontColor: Color
        private var dmgFontScale by Delegates.notNull<Float>()
        private var dmgDuration by Delegates.notNull<Int>()
        private var dmgRaiseHeight by Delegates.notNull<Float>()
        private var dmgStartFadeoutAt by Delegates.notNull<Int>()

        private var xShake by Delegates.notNull<Float>()
        private var yShake by Delegates.notNull<Float>()
        private var xSpeedMultiplier by Delegates.notNull<Float>()
        private var ySpeedMultiplier by Delegates.notNull<Float>()
        private var shakeDuration by Delegates.notNull<Float>()

        private var xCharge by Delegates.notNull<Float>()
        private var yCharge by Delegates.notNull<Float>()
        private var chargeDuration by Delegates.notNull<Float>()
        private lateinit var chargeInterpolation: Interpolation

        private lateinit var coverStackDestroyedParticlesName: String
        private lateinit var coverStackDamagedParticlesName: String

        private var bufferTime by Delegates.notNull<Int>()

        fun init(config: OnjObject) {
            val dmgOnj = config.get<OnjObject>("playerLivesAnimation")

            dmgFontName = dmgOnj.get<String>("font")
            dmgFontScale = dmgOnj.get<Double>("fontScale").toFloat()
            dmgDuration = (dmgOnj.get<Double>("duration") * 1000).toInt()
            dmgRaiseHeight = dmgOnj.get<Double>("raiseHeight").toFloat()
            dmgStartFadeoutAt = (dmgOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            dmgFontColor = dmgOnj.get<Color>("negativeFontColor")

            val shakeOnj = config.get<OnjObject>("shakeAnimation")

            xShake = shakeOnj.get<Double>("xShake").toFloat()
            yShake = shakeOnj.get<Double>("yShake").toFloat()
            xSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
            ySpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
            shakeDuration = shakeOnj.get<Double>("duration").toFloat()

            val chargeOnj = config.get<OnjObject>("enemyChargeAnimation")

            xCharge = chargeOnj.get<Double>("xCharge").toFloat()
            yCharge = chargeOnj.get<Double>("yCharge").toFloat()
            chargeDuration = chargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
            chargeInterpolation = Utils.interpolationOrError(chargeOnj.get<String>("interpolation"))

            val coverStackOnj = config.get<OnjObject>("coverStackParticles")

            coverStackDamagedParticlesName = coverStackOnj.get<String>("damaged")
            coverStackDestroyedParticlesName = coverStackOnj.get<String>("destroyed")

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()
        }

        /**
         * reads an array of Enemies from on an OnjArray
         */
        fun getFrom(
            enemiesOnj: OnjArray
        ): List<Enemy> = enemiesOnj
            .value
            .map {
                it as OnjObject
                val gameController = FourtyFive.currentGame!!
                val screenDataProvider = gameController.curScreen
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
                    it.get<Color>("detailFontColor")
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

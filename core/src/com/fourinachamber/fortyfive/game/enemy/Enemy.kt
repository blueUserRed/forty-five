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
    val baseHealthPerTurn: Int,
    val baseDamage: Int,
    val turnCount: IntRange,
    private val creator: (health: Int, damage: Int) -> Enemy
) {
    fun create(health: Int, damage: Int): Enemy = creator(health, damage)
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
    val health: Int,
    val scaleX: Float,
    val scaleY: Float,
    val coverIconScale: Float,
    val detailFont: BitmapFont,
    val detailFontScale: Float,
    val detailFontColor: Color,
    val damage: Int,
    val actions: List<Pair<Int, EnemyAction>>,
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

    private val statusEffects: MutableList<StatusEffect> = mutableListOf()

    init {
        actor = EnemyActor(this, screen)
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

    fun executeStatusEffectsAfterTurn(): Timeline? {
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
                val timelineToInclude = effect.executeAfterRevolverRotation(gameController) ?: continue
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

    @MainThreadOnly
    fun damagePlayer(damage: Int, gameController: GameController): Timeline = Timeline.timeline {
        val screen = gameController.curScreen
        val chargeTimeline = GraphicsConfig.chargeTimeline(actor)

        val overlayAction = GraphicsConfig.damageOverlay(screen)

        include(chargeTimeline)

        includeAction(overlayAction)
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
    fun damage(damage: Int): Timeline = Timeline.timeline {
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
    }

    companion object {

        private var instanceCounter = 0

        fun readEnemies(arr: OnjArray): List<EnemyPrototype> = arr
            .value
            .map { it as OnjObject }
            .map {
                EnemyPrototype(
                    it.get<String>("name"),
                    it.get<Long>("baseHealthPerTurn").toInt(),
                    it.get<Long>("baseDamage").toInt(),
                    it.get<OnjArray>("fightDuration").toIntRange()
                ) { health, damage -> readEnemy(it, health, damage) }
            }
        
        fun readEnemy(onj: OnjObject, health: Int, damage: Int): Enemy {
            val gameController = FortyFive.currentGame!!
            val curScreen = gameController.curScreen
            val drawableHandle = onj.get<String>("texture")
            val coverIconHandle = onj.get<String>("coverIcon")
            val detailFont = ResourceManager.get<BitmapFont>(curScreen, onj.get<String>("detailFont"))
            val actions = onj
                .get<OnjArray>("actions")
                .value
                .map { it as OnjNamedObject }
                .map { it.get<Long>("weight").toInt() to EnemyAction.fromOnj(it) }
            return Enemy(
                onj.get<String>("name"),
                drawableHandle,
                coverIconHandle,
                health,
                onj.get<Double>("scaleX").toFloat(),
                onj.get<Double>("scaleY").toFloat(),
                onj.get<Double>("coverIconScale").toFloat(),
                detailFont,
                onj.get<Double>("detailFontScale").toFloat(),
                onj.get<Color>("detailFontColor"),
                damage,
                actions,
                curScreen
            )
        }

    }

}

/**
 * used for representing an enemy on the screen
 */
class EnemyActor(
    val enemy: Enemy,
    private val screen: OnjScreen
) : CustomVerticalGroup(screen), ZIndexActor, AnimationActor {

    override var inAnimation: Boolean = false
    override var fixedZIndex: Int = 0
    private val image: CustomImageActor = CustomImageActor(enemy.drawableHandle, screen)
    private val coverIcon: CustomImageActor = CustomImageActor(enemy.coverIconHandle, screen)
    val coverText: CustomLabel = CustomLabel(screen, "", Label.LabelStyle(enemy.detailFont, enemy.detailFontColor))
    private var enemyBox = CustomHorizontalGroup(screen)
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
        image.setScale(enemy.scaleX, enemy.scaleY)
        image.reportDimensionsWithScaling = true
        image.ignoreScalingWhenDrawing = true
        coverIcon.setScale(enemy.coverIconScale)
        coverIcon.reportDimensionsWithScaling = true
        coverIcon.ignoreScalingWhenDrawing = true

        val coverInfoBox = CustomVerticalGroup(screen)
        coverInfoBox.addActor(coverIcon)
        coverInfoBox.addActor(coverText)

        enemyBox.addActor(coverInfoBox)
        enemyBox.addActor(image)

        addActor(enemyBox)
        addActor(healthLabel)
        addActor(statusEffectDisplay)
        updateText()
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
    fun onRevolverTurn() = statusEffectDisplay.updateRemainingTurns()

    /**
     * updates the description text of the actor
     */
    fun updateText() {
        coverText.setText("${enemy.currentCover}")
        healthLabel.setText("${enemy.currentHealth}/${enemy.health}")
    }

}

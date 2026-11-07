package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.enemy.StatusBar
import com.microwavestudios.fortyfive.game.widgets.StatusEffectBarCreator
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.plugin.PluginApi
import com.microwavestudios.fortyfive.screen.actors.AnimatedActor
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screens.EncounterScreen.UpdateUiEvent
import com.microwavestudios.fortyfive.screen.screens.EncounterScreenOffsetProvider
import com.microwavestudios.fortyfive.utils.Timeline

interface EnemyActor {

    fun createActor(
        parent: CustomGroup,
        creator: ScreenCreator,
        coords: Vector2,
        bgScale: Float,
        offsetProvider: EncounterScreenOffsetProvider,
        defaultActionIndicatorCreator: CustomGroup.(Enemy) -> Unit,
    )

}

object EnemyActorFactory {

    private val selectors: MutableList<(Enemy) -> EnemyActorCreator?> = mutableListOf()

    init {
        addSelector {
            { enemy: Enemy, controller: GameController -> DefaultEnemyActor(enemy, controller) }
        }
    }

    fun addSelector(selector: (Enemy) -> EnemyActorCreator?) {
        selectors.add(0, selector)
    }

    fun createEnemyActor(enemy: Enemy, controller: GameController): EnemyActor {
        selectors.forEach { selector ->
            val cur = selector(enemy)
            if (cur != null) return cur(enemy, controller)
        }
        throw RuntimeException("no actor creator for enemy $enemy")
    }

}

@PluginApi
typealias EnemyActorCreator = (Enemy, GameController) -> EnemyActor

class DefaultEnemyActor(
    private val enemy: Enemy,
    private val controller: GameController
) : EnemyActor {

    override fun createActor(
        parent: CustomGroup,
        creator: ScreenCreator,
        coords: Vector2,
        bgScale: Float,
        offsetProvider: EncounterScreenOffsetProvider,
        defaultActionIndicatorCreator: CustomGroup.(Enemy) -> Unit,
    ) {
        with(creator) {
            createWithReceiver(parent, coords, bgScale, offsetProvider, defaultActionIndicatorCreator)
        }
    }

    private fun ScreenCreator.createWithReceiver(
        parent: CustomGroup,
        coords: Vector2,
        bgScale: Float,
        offsetProvider: EncounterScreenOffsetProvider,
        defaultActionIndicatorCreator: CustomGroup.(Enemy) -> Unit,
    ) = with(parent) {
        val enemy = enemy
        val gameEvents = controller.gameEvents
        box {
            enemy.actor = this
            flexDirection = FlexDirection.COLUMN
            val scale = enemy.inherentScale * bgScale
            x = coords.x
            y = coords.y
            width = 200f
            syncHeight()

            gameEvents.watchFor<UpdateUiEvent> {
                drawOffsetX = offsetProvider.bgOffX * 1.1f
                drawOffsetY = offsetProvider.bgOffY * 1.1f
            }

            fun chargeTimeline(): Timeline = Timeline.timeline {
                val origX = x
                val forwardAction = MoveToAction()
                forwardAction.x = origX - 50f
                forwardAction.y = y
                forwardAction.duration = 0.2f
                forwardAction.interpolation = Interpolation.pow4
                val backAction = MoveToAction()
                backAction.x = origX
                backAction.y = y
                backAction.duration = 0.4f
                backAction.interpolation = Interpolation.smooth
                action { addAction(forwardAction) }
                delayUntil { forwardAction.isComplete }
                action {
                    removeAction(forwardAction)
                    addAction(backAction)
                }
                delayUntil { backAction.isComplete }
                action { removeAction(backAction) }
            }

            enemy.enemyEvents.watchFor<Enemy.PlayChargeAnimationEvent> { event ->
                event.timeline.resolve(chargeTimeline())
            }

            defaultActionIndicatorCreator(enemy)

            group {

                keyboardFocusable = KeyboardFocusable.LEAF
                touchable = Touchable.enabled

                observeInputState(
                    GameInputs.States.focused,
                    {
                        gameEvents.fire(GameControllerImpl.Events.EnemyClicked(enemy, true))
                    },
                    {
                        gameEvents.fire(GameControllerImpl.Events.EnemyClicked(null, true))
                    },
                )

                onInput(GameInputs.targetEnemy) {
                    gameEvents.fire(GameControllerImpl.Events.EnemyClicked(enemy, false))
                }

                onInput(GameInputs.shootEnemy) {
                    gameEvents.fire(GameControllerImpl.Events.ShootAtEnemy(enemy))
                }


                image {
                    backgroundHandle = "encounter_enemy_shadow"
                    relativeWidth(140f * scale * enemy.shadowScale)
                    height = 110f * scale * enemy.shadowScale
                    centerX()
                    y = -20f
                    enemy.enemyEvents.watchFor<Enemy.EnemyDefeated> {
                        isVisible = false
                    }
                }

                val enemyImage = image {
                    val heightPercent = 1f
                    backgroundHandle = enemy.drawableHandle
                    centerX()
                    y = 0f
                    onLayout {
                        height = 400f * heightPercent * scale
                        val drawable = drawable ?: return@onLayout
                        width = height * (drawable.minWidth / drawable.minHeight)
                    }
                    enemy.enemyEvents.watchFor<Enemy.EnemyDefeated> {
                        isVisible = false
                    }
                }

                image {
                    backgroundHandle = "enemy_gravestone"
                    centerX()
                    relativeWidth(100f * scale)
                    heightByAspectRatio(470.0 / 520.0)
                    y = 0f
                    isVisible = false
                    enemy.enemyEvents.watchFor<Enemy.EnemyDefeated> {
                        isVisible = true
                    }
                }

                relativeWidth(100f)
                onLayoutAndNow { height = enemyImage.height }

                image {
                    backgroundHandle = "card_symbol_marked"
                    centerX()
                    y = 180f * scale
                    relativeWidth(40f)
                    touchable = Touchable.disabled
                    onLayoutAndNow { height = width }
                    animateRotationSinus(
                        amplitude = 5.5f,
                    )
                    animateUpAndDownSinus(
                        method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                        amplitude = 14f,
                        frequency = 0.4f
                    )
                    gameEvents.watchFor<GameControllerImpl.Events.EnemySelectionChanged> { (e, controller) ->
                        val onlyOne = controller.allEnemies.size == 1
                        val enemySelected = e === enemy
                        isVisible = !onlyOne && enemySelected
                        invalidate()
                    }
                }
            }

            group {
                height = 40f
                width = 230f
                val statusBar = StatusBar(screen, enemy)
                actor(statusBar) {
                    relativeWidth(100f)
                    relativeHeight(100f)
                }
            }

            val statusEffectBar = StatusEffectBarCreator.createStatusBar(
                this@createWithReceiver,
                StatusEffectBarCreator.StatusEffectBarTarget.EnemyTarget(enemy, gameEvents)
            )

            actor(statusEffectBar) {
                relativeWidth(87f)
                logicalOffsetX = 30f
                height = 70f
            }

        }
    }

}

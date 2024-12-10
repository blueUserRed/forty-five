package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.animation.AnimState
import com.fourinachamber.fortyfive.animation.xPositionAbstractProperty
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.game.enemy.StatusBar
import com.fourinachamber.fortyfive.keyInput.KeyActionFactory
import com.fourinachamber.fortyfive.keyInput.KeyInputCondition
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.KeyInputMapEntry
import com.fourinachamber.fortyfive.keyInput.KeyInputMapKeyEntry
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.gameWidgets.NewCardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.gameWidgets.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.AdvancedText
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.AnimatedActor
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.onSelect
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import com.fourinachamber.fortyfive.utils.AdvancedTextParser.*
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.plus

class GameScreen : ScreenCreator() {

    override val name: String = "gameScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 0
    )

    private val cardDragAndDrop = DragAndDrop()

    val gameEvents: EventPipeline = EventPipeline()

    private lateinit var reservesAnimationTarget: Actor
    private lateinit var deckAnimationTarget: Actor

    private lateinit var enemyParent: CustomGroup

    private val revolver by lazy {
        Revolver(
            "revolver_drum",
            "revolver_slot_texture",
            200f,
            screen
        ).apply {
            slotSize = 110f
            cardScale = 0.9f
            animationDuration = 0.2f
            radius = 140f
            rotationOff = (Math.PI / 2f) + (2f * Math.PI) / 5f
            cardZIndex = 100
            initDragAndDrop(cardDragAndDrop)
        }
    }

    private val cardHand by lazy {
        NewCardHand(
            screen,
            300f,
            596f * 0.22f,
            70f
        )
    }

    init {
        bindEventHandlers()
    }

    override fun getRoot(): Group = newGroup {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            backgroundHandle = "game_screen_player"
            x = 20f
            y = 0f
            height = worldHeight
            width = (1305f / 1512f) * worldHeight
        }

        group {
            enemyParent = this@group
            x = 800f
            y = 250f
            width = 800f
            height = 600f
        }

        playerBar()

    }

    private fun createEnemy(x: Float, y: Float, enemy: Enemy): Float = with(enemyParent) {

        val enemyHeight = 400f
        val enemyWidth = enemyHeight * 0.6f

        var enemySelected = false

        box {
            flexDirection = FlexDirection.COLUMN
            this.x = x
            this.y = y
            width = enemyWidth
            height = enemyHeight

            group = "enemies"
            setFocusableTo(true, this)
            isSelectable = true
            onSelect {
                if (enemySelected) return@onSelect
                gameEvents.fire(NewGameController.Events.EnemySelected(enemy))
            }

            box {

                fun showAction(
                    text: String,
                    iconHandle: String,
                    additionalDamage: String? = null,
                    additionalDamageIcon: String? = null
                ) {
                    image {
                        backgroundHandle = iconHandle
                        width = 60f
                        height = 60f
                    }
                    group {
                        width = 60f
                        height = 40f
                        backgroundHandle = "wood_box"
                        val newText = if (additionalDamageIcon != null) {
                            "$text + ?1$additionalDamage?1 §§${additionalDamageIcon}§§"
                        } else {
                            text
                        }
                        advancedText("roadgeek", Color.FortyWhite, 0.9f) {
                            val redEffect = AdvancedTextEffect.AdvancedColorTextEffect("?R", Color.Red)
                            relativeHeight(58f)
                            setRawText(newText, listOf(redEffect))
                            centerX()
                            centerY()
                        }
                    }
                }

                relativeWidth(100f)
                animateUpAndDownSinus(
                    method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                    frequency = 0.3f,
                    amplitude = 6f
                )
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER
                height = enemyHeight * 0.15f
                enemy.enemyEvents.watchFor<Enemy.EnemyActionChangedEvent> { event ->
                    this.clear()
                    when (val nextAction = event.nextAction) {
                        is NextEnemyAction.ShownEnemyAction -> showAction(
                            nextAction.action.indicatorText ?: "",
                            nextAction.action.prototype.iconHandle,
                            event.additionalDamage.toString(),
                            event.additionalDamageIcon
                        )
                        is NextEnemyAction.None -> {}
                        is NextEnemyAction.HiddenEnemyAction -> showAction("?",  "enemy_action_unknown")
                    }
                }
            }

            group {
                relativeWidth(100f)
                height = enemyHeight * 0.65f

                image {
                    backgroundHandle = enemy.drawableHandle
                    relativeHeight(100f)
                    centerX()
                    centerY()
                    onLayout {
                        val drawable = drawable ?: return@onLayout
                        width = height * (drawable.minWidth / drawable.minHeight)
                    }
                }

                image {
                    backgroundHandle = "card_symbol_marked"
                    centerX()
                    centerY()
                    relativeWidth(60f)
                    onLayoutAndNow { height = width }
                    animateRotationSinus(
                        amplitude = Math.PI.toFloat() * 1.8f,
                    )
                    animateUpAndDownSinus(
                        method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                        amplitude = 14f,
                        frequency = 0.4f
                    )
                    gameEvents.watchFor<NewGameController.Events.EnemySelected> { (e) ->
                        enemySelected = e === enemy
                        isVisible = enemySelected
                    }
                }
            }

            group {
                relativeWidth(100f)
                height = enemyHeight * 0.2f
                val statusBar = StatusBar(screen, enemy)
                actor(statusBar) {
                    relativeWidth(100f)
                    relativeHeight(100f)
                }
            }

        }
        return enemyWidth
    }

    private fun CustomGroup.playerBar() = group {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldWidth * (505f / 1920f)
        backgroundHandle = "player_bar"

        shootButton()
        holsterButton()

        actor(revolver) {
            touchable = Touchable.enabled
            name("revolver")
            centerX()
            y = -30f
            syncDimensions()
        }

        actor(cardHand) {
            name("cardHand")
            centerX()
            y = 0f
            width = worldWidth
            gameEvents.link(events)
        }

        group {
            backgroundHandle = "wood_box"
            x = 70f
            y = 180f
            width = 120f
            height = 120f
            reservesAnimationTarget = this
            animateRotationSinus(
                frequency = 0.15f
            )

            image {
                backgroundHandle = "reserves_texture"
                width = 60f
                height = 60f
                x = 30f
                y = 90f
            }

            label("red_wing", "0/0", Color.White) {
                setFontScale(1.1f)
                centerX()
                centerY()
                gameEvents.watchFor<NewGameController.Events.ReservesChanged> { (_, new) ->
                    setText("${new}/${NewGameController.Config.baseReserves}")
                }
            }
        }

        group {
            backgroundHandle = "wood_box"
            x = worldWidth - 70f - 120f
            y = 180f
            width = 120f
            height = 120f
            deckAnimationTarget = this

            animateRotationSinus(
                frequency = 0.15f
            )

            image {
                backgroundHandle = "deck_icon"
                width = 60f
                height = 60f
                x = 30f
                y = 90f
            }

            label("red_wing", "{game.cardsInStack}", Color.White, isTemplate = true) {
                setFontScale(1.1f)
                centerX()
                centerY()
            }
        }

    }

    private fun CustomGroup.shootButton() {
        group {
            name("shoot_button")
            touchable = Touchable.enabled
            x = 370f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f, 100, Interpolation.pow2),
                AnimState("hover", 360f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 200),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "shoot_button"
            styles(
                normal = {
                    backgroundHandle = "shoot_button_texture"
                    xAnim.state("open")
                },
                focused = {
                    backgroundHandle = "shoot_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                gameEvents.fire(NewGameController.Events.Shoot)
            }
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    isSelectable = false
                    isFocusable = false
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    isSelectable = true
                    isFocusable = true
                    touchable = Touchable.enabled
                }
            }
        }

        group {
            name("pass_button")
            var closed = true
            touchable = Touchable.disabled
            x = 600f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f, 100, Interpolation.pow2),
                AnimState("hover", 360f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 200),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = false
            isFocusable = false
            group = "pass_button"
            onSelect {
                screen.deselectActor(this)
            }
            styles(
                normal = {
                    backgroundHandle = "pass_button_texture"
                    xAnim.state(if (closed) "closed" else "open")
                },
                focused = {
                    backgroundHandle = "pass_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    isSelectable = false
                    isFocusable = false
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    isSelectable = true
                    isFocusable = true
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    private fun CustomGroup.holsterButton() {
        group {
            name("holster_button")
            touchable = Touchable.enabled
            x = 990f
            y = 60f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 990f, 100, Interpolation.pow2),
                AnimState("hover", 1000f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 200),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "holster_button"
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                gameEvents.fire(NewGameController.Events.Holster)
            }
            styles(
                normal = {
                    backgroundHandle = "end_turn_button_texture"
                    xAnim.state("open")
                },
                focused = {
                    backgroundHandle = "end_turn_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    isSelectable = false
                    isFocusable = false
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    isSelectable = true
                    isFocusable = true
                    touchable = Touchable.enabled
                }
            }
        }

        group {
            name("parry_button")
            var closed = true
            touchable = Touchable.disabled
            x = 990f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 990f, 100, Interpolation.pow2),
                AnimState("hover", 1000f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 200),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = false
            isFocusable = false
            group = "parry_button"
            onSelect {
                screen.deselectActor(this)
            }
            styles(
                normal = {
                    backgroundHandle = "parry_button_texture"
                    xAnim.state(if (closed) "closed" else "open")
                },
                focused = {
                    backgroundHandle = "parry_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    isSelectable = false
                    isFocusable = false
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    isSelectable = true
                    isFocusable = true
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, false),
        NewGameController(screen, gameEvents)
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(KeyInputMap.createFromKotlin(listOf(

        KeyInputMapEntry(
            priority = 20,
            condition = KeyInputCondition.Always,
            singleKeys = listOf(
                KeyInputMapKeyEntry(Keys.S, modifierKeys = listOf(Keys.SHIFT_LEFT)),
                KeyInputMapKeyEntry(Keys.S, modifierKeys = listOf(Keys.SHIFT_RIGHT)),
            ),
            defaultActions = listOf(
                KeyActionFactory.getAction("FocusSpecific", "shoot_button"),
                KeyActionFactory.getAction("FocusSpecific", "pass_button"),
            )
        ),

        KeyInputMapEntry(
            priority = 20,
            condition = KeyInputCondition.Always,
            singleKeys = listOf(
                KeyInputMapKeyEntry(Keys.H, modifierKeys = listOf(Keys.SHIFT_LEFT)),
                KeyInputMapKeyEntry(Keys.H, modifierKeys = listOf(Keys.SHIFT_RIGHT)),
            ),
            defaultActions = listOf(
                KeyActionFactory.getAction("FocusSpecific", "holster_button"),
                KeyActionFactory.getAction("FocusSpecific", "parry_button"),
            )
        ),

    ), screen))

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf("shoot_button", "holster_button", "pass_button", "parry_button")
                ),
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(NewCardHand.cardFocusGroupName, RevolverSlot.revolverSlotFocusGroupName, "enemies")
                ),
                SelectionTransition(
                    TransitionType.LastResort,
                    groups = listOf(RevolverSlot.revolverSlotFocusGroupName),
                )
            )
        )
    )

    private fun orbAnimationTimeline(source: Actor, target: Actor, amount: Int, isReserves: Boolean): Timeline = Timeline.timeline {
        val renderPipeline = FortyFive.currentRenderPipeline ?: return@timeline
        repeat(amount) {
            action {
                SoundPlayer.situation("orb_anim_playing", screen)
                renderPipeline.addOrbAnimation(
                    GraphicsConfig.orbAnimation(
                        source.localToStageCoordinates(Vector2(0f, 0f)) +
                                Vector2(source.width / 2, source.height / 2),
                        target.localToStageCoordinates(Vector2(0f, 0f)) +
                                Vector2(target.width / 2, target.height / 2),
                        isReserves,
                        renderPipeline
                    ))
            }
            delay(50)
        }
    }

    private fun reservesPaidAnim(amount: Int, animTarget: Actor): Timeline =
        orbAnimationTimeline(reservesAnimationTarget, animTarget, amount = amount, isReserves = true)

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline =
        orbAnimationTimeline(animSource, reservesAnimationTarget, amount = amount, isReserves = true)


    private fun bindEventHandlers() {
        gameEvents.watchFor<NewGameController.Events.ReservesChanged>(::reservesChangedAnim)
        gameEvents.watchFor<NewGameController.Events.PlayCardOrbAnimation> { event ->
            event.orbAnimationTimeline = orbAnimationTimeline(deckAnimationTarget, event.targetActor, 1, false)
        }
        gameEvents.watchFor<NewGameController.Events.SetupEnemies>(::setupEnemies)
    }

    private fun setupEnemies(event: NewGameController.Events.SetupEnemies) {
        var x = 10f
        var y = 160f
        event.enemies.forEach { enemy ->
            val neededWidth = createEnemy(x, y, enemy)
            x += neededWidth
            y -= 30f
        }
    }

    private fun reservesChangedAnim(event: NewGameController.Events.ReservesChanged) {
        val (old, new, source, controller) = event
        source ?: return
        val amount = new - old
        val anim = when {
            amount > 0 -> reservesGainedAnim(amount, source)
            amount < 0 -> reservesPaidAnim(amount, source)
            else -> null
        }
        anim?.let { controller.dispatchAnimTimeline(it) }
    }

}

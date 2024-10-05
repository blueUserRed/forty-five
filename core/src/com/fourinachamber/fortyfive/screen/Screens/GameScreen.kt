package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.gameWidgets.NewCardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.gameWidgets.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
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

        playerBar()

    }

    private fun CustomGroup.playerBar() = group {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldWidth * (505f / 1920f)
        backgroundHandle = "player_bar"

        shootButton()

        actor(revolver) {
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

            image {
                backgroundHandle = "deck_icon"
                width = 60f
                height = 60f
                centerX()
                centerY()
            }
        }

    }

    private fun CustomGroup.shootButton() {

        group {
            touchable = Touchable.enabled
            x = 350f
            y = 55f
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "shoot_button"
            debug()
            styles(
                normal = {
                    backgroundHandle = "shoot_button_texture"
                },
                focused = {
                    backgroundHandle = "shoot_button_hover_texture"
                }
            )

        }

    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, false),
        NewGameController(screen, gameEvents)
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(KeyInputMap.createFromKotlin(listOf(), screen))

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(NewCardHand.cardFocusGroupName, RevolverSlot.revolverSlotFocusGroupName)
                ),
                SelectionTransition(
                    TransitionType.LastResort,
                    groups = listOf(RevolverSlot.revolverSlotFocusGroupName, "shoot_button"),
                )
            )
        )
    )

    private fun orbAnimationTimeline(source: Actor, target: Actor, amount: Int): Timeline = Timeline.timeline {
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
                        true,
                        renderPipeline
                    ))
            }
            delay(50)
        }
    }

    private fun reservesPaidAnim(amount: Int, animTarget: Actor): Timeline =
        orbAnimationTimeline(reservesAnimationTarget, animTarget, amount = amount)

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline =
        orbAnimationTimeline(animSource, reservesAnimationTarget, amount = amount)


    private fun bindEventHandlers() {
        gameEvents.watchFor<NewGameController.Events.ReservesChanged>(::reservesChangedAnim)
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

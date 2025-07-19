package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.PropertyAnimation
import com.microwavestudios.fortyfive.animation.yPositionAbstractProperty
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.widgets.TextEffectEmitter
import com.microwavestudios.fortyfive.game.widgets.textEffectEmitter
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.run.RunReward
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.commonComponents.RunCardCreator.getSharedRunCard
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.alpha
import kotlin.reflect.KClass

class WinRunScreen : ScreenCreator() {

    override val name: String = "winRunScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "microwave_studios_brown_texture"

    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 0)

    private val events: EventPipeline = EventPipeline()

    private val timelines: TimelineController = TimelineController()

    private lateinit var blackBackground: CustomBox

    private val modal: InputManager.Modal by lazy {
        InputManager.Modal(listOf(), screen)
    }

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        box {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight

            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.SPACE_BETWEEN

            mainSection()
            confirmButton()
        }

        blackBackground = box {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            alpha = 0.3f
            backgroundHandle = "black_texture"
            touchable = Touchable.enabled
            isVisible = false
        }

        cashPopup()

        addDefaultOverlays(
            worldWidth,
            worldHeight,
            events,
            hasTutorial = false,
            hasTitleScreen = false,
            hasSettings = false
        )
    }

    private fun CustomGroup.cashPopup() {

        val profile = FortyFive.profileManager.currentProfile!!

        lateinit var cashLabel: CustomLabel
        lateinit var cashGroup: CustomGroup

        val popup = box {
            centerX()
            y = -400f
            width = 500f
            height = 200f
            backgroundHandle = "detail_widget_background_big"

            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND

            group {
                width = 80f
                height = 70f
                backgroundHandle = "cash_symbol"
                badTexture("cash symbol on win screen popup", lowRes = true)
            }

            cashGroup = group {
                width = 300f
                height = 70f
                backgroundHandle = "common_button_default"
                badTexture("background on win screen cash popup")

                cashLabel = label("red_wing", "${profile.playerMoney}$", color = Color.FortyWhite) {
                    centerY()
                    x = 0f
                    relativeWidth(90f)
                    setAlignment(Align.right)
                }
            }

        }

        val yAnim = PropertyAnimation(
            popup,
            popup.yPositionAbstractProperty(),
            Float::class,
            360,
            Interpolation.pow4,
            "hidden",
            states = arrayOf(
                AnimState("hidden", -400f),
                AnimState("shown", popup.parent.height / 2 - popup.height / 2),
                AnimState("finished", worldHeight + 400)
            )
        )

        val redWing = FortyFive.resourceManager.forceGet<BitmapFont>(screen, screen.lifetime, "red_wing")

        val textEmitter = cashGroup.textEffectEmitter(mapOf(
            "cash" to TextEffectEmitter.TextAnimationConfig(
                redWing,
                Color.DarkGreen,
                1f,
                speed = 150f..180f,
                spawnVarianceX = 30f,
                spawnVarianceY = 30f,
                animationDuration = 1000..1500
            )
        ))

        fun createTimeline(amount: Int) = Timeline.timeline {

            includeAction(yAnim.stateAction("shown"))

            delay(500)

            action {
                textEmitter.playAnimation("+$amount$", "cash")
                profile.earnMoney(amount)
                cashLabel.setText("${profile.playerMoney}$")
            }

            delay(600)

            includeAction(yAnim.stateAction("finished"))
        }

        events.watchFor<CashPopup> { event ->
            event.timeline = createTimeline(event.cashAmount)
        }
    }

    private fun CustomBox.confirmButton() = box {
        relativeWidth(100f)
        relativeHeight(20f)
        horizontalAlign = CustomAlign.CENTER
        verticalAlign = CustomAlign.CENTER

        box(backgroundHints = buttonBackgroundHints()) {
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.CENTER
            height = 70f
            width = 180f
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            defaultButtonBackgrounds()

            label("red_wing", "Claim Rewards", color = Color.FortyWhite)

            onInput(GameInputs.interact) {
                val profile = FortyFive.profileManager.currentProfile!!
                val event = ClaimRewardsEvent()
                event.append {
                    blackBackground.isVisible = true
                    modal.push()
                }
                events.fire(event)
                timelines.appendMainTimeline(event.createTimeline())
                timelines.appendMainTimeline(Timeline.timeline {
                    action {
                        profile.winRun()
                        FortyFive.screenManager.screenFinished()
                    }
                })
            }
        }
    }

    private fun CustomBox.mainSection() = box {
        relativeWidth(100f)
        relativeHeight(80f)

        flexDirection = FlexDirection.ROW
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.SPACE_AROUND

        val profile = FortyFive.profileManager.currentProfile!!
        val run = profile.activeRun!!

        box {
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            relativeWidth(25f)
            relativeHeight(50f)

            label("red_wing", "completed run:", Color.FortyWhite)
            actor(getSharedRunCard(run))
        }

        box {
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            relativeWidth(25f)
            relativeHeight(50f)

            label("red_wing", "rewards:", Color.FortyWhite)
            rewards(run.rewards)
        }
    }

    private fun CustomBox.rewards(rewards: List<RunReward>): Unit = rewards.forEach { reward ->
        when (reward) {
            is RunReward.Cash -> cashReward(reward)
        }
    }

    private fun CustomBox.cashReward(reward: RunReward.Cash) {
        box {
            width = 400f
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            height = 70f
            backgroundHandle = "win_popup_item_cash"

            image {
                name("reward_cash_symbol")
                width = 40f
                height = 30f
                backgroundHandle = "cash_symbol"
                marginLeft = 10f
                marginRight = 10f
            }

            label("red_wing", "You get ${reward.amount}$", Color.FortyWhite)
        }

        events.watchFor<ClaimRewardsEvent> { event -> event.append {
            later {
                val popupEvent = CashPopup(cashAmount = reward.amount)
                events.fire(popupEvent)
                include(popupEvent.timeline!!)
            }
        } }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(timelines)


    private class ClaimRewardsEvent {

        val dsl = Timeline.TimelineBuilderDSL()

        inline fun append(block: Timeline.TimelineBuilderDSL.() -> Unit) {
            block(dsl)
        }

        fun createTimeline(): Timeline = dsl.build()
    }

    private class CashPopup(var timeline: Timeline? = null, val cashAmount: Int)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = WinRunScreen::class
    }

}
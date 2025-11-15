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
import com.microwavestudios.fortyfive.game.card.RandomCardSelection
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
import com.microwavestudios.fortyfive.utils.*
import kotlin.reflect.KClass

class WinRunScreen : ScreenCreator() {

    override val name: String = "winRunScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String = "microwave_studios_brown_texture"

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
    )

    private val events: EventPipeline = EventPipeline()

    private val timelines: TimelineController = TimelineController()

    private lateinit var blackBackground: CustomBox

    private val modal: InputManager.Modal by lazy {
        InputManager.Modal(listOf(), screen)
    }

    private val profile = FortyFive.profileManager.currentProfile!!

    private val cardsToExtract: List<String> = FortyFive.profileManager.currentProfile!!.extractableCards()

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

        extractionPopup()
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

    private fun CustomGroup.extractionPopup() {

        val allCardProtos = RandomCardSelection.allCardPrototypes
        val cardProtos = cardsToExtract.map { name -> allCardProtos.find { it.name == name }!! }
        val cards = cardProtos.map { it.create(screen) }
        cards.forEach { screen.lifetime.tieDisposable(it) }

        val popupWidth = worldWidth * 0.6f
        val popupHeight = worldHeight * 0.7f

        val modal = InputManager.Modal(listOf("extract-cards-popup-button"), screen)
        val filter = InputManager.FocusFilter(listOf("extract-cards-popup-button"), screen)
        filter.start()

        val finishedPromise: Promise<Unit> = Promise()

        val popup = group {
            width = popupWidth
            height = popupHeight
            backgroundHandle = "map_extraction_background"
            centerX()

            label("red wing", "Cards that will be added to your collection", Color.FortyWhite, 32) {
                relativeWidth(100f)
                setAlignment(Align.center)
                centerX()
                syncHeight()
                onLayoutAndNow { y = parent.height - height - 30f }
            }

            group {
                backgroundHandle = "map_extraction_card_background_blue"
                relativeWidth(90f)
                relativeHeight(90f)
                centerX()
                centerY()
            }

            box(isScrollable = true) {
                this as CustomScrollableBox
                backgroundHandle = "map_extraction_card_background_white"
                relativeWidth(75f)
                relativeHeight(70f)
                centerX()
                centerY()
                paddingTop = 15f
                paddingLeft = 15f
                paddingRight = 15f
                val widthPerCard = (popupWidth * 0.75f - 30f) / 5f
                scrollDirectionStart = CustomDirection.TOP
                flexDirection = FlexDirection.ROW
                wrap = CustomWrap.WRAP
                addScrollbarFromDefaults(
                    CustomDirection.RIGHT,
                    "backpack_scrollbar",
                    "backpack_scrollbar_background",
                )
                cards.forEach { card ->
                    box {
                        width = widthPerCard
                        height = widthPerCard
                        verticalAlign = CustomAlign.CENTER
                        horizontalAlign = CustomAlign.CENTER
                        actor(card.actor) {
                            width = widthPerCard * 0.9f
                            height = widthPerCard * 0.9f
                        }
                    }
                }
            }

            box(backgroundHints = buttonBackgroundHints()) {
                centerX()
                y = 20f
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER
                height = 50f
                width = 110f
                keyboardFocusable = KeyboardFocusable.LEAF
                touchable = Touchable.enabled
                joinGroup("extract-cards-popup-button")
                defaultButtonConfig()

                label("red wing", "Ok", Color.FortyWhite, 32) {
                    touchable = Touchable.disabled
                    syncDimensions()
                }

                onInput(GameInputs.interact) {
                    finishedPromise.resolve(Unit)
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
                AnimState("hidden", -700f),
                AnimState("shown", popup.parent.height / 2 - popup.height / 2),
                AnimState("finished", worldHeight + 700)
            )
        )

        fun createTimeline() = Timeline.timeline {
            includeAction(yAnim.stateAction("shown"))
            action {
                filter.end()
                modal.push()
            }
            waitForPromise(finishedPromise)
            action {
                filter.start()
                modal.finished()
            }
            includeAction(yAnim.stateAction("finished"))
        }

        events.watchFor<ExtractionPopup> { event ->
            event.timeline = createTimeline()
        }
    }

    private fun CustomGroup.cashPopup() {

        lateinit var cashLabel: NewLabel
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

                cashLabel = label("red wing", "${profile.playerMoney}$", Color.FortyWhite, 32) {
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

        val textEmitter = cashGroup.textEffectEmitter(mapOf(
            "cash" to TextEffectEmitter.TextAnimationConfig(
                TextEffectEmitter.roadgeek,
                Color.DarkGreen,
                0.8f,
                positiveSpeed = 3f..5f,
            )
        ))

        fun createTimeline(amount: Int) = Timeline.timeline {

            includeAction(yAnim.stateAction("shown"))

            delay(500)

            action {
                textEmitter.playAnimation("+$amount$", "cash")
                profile.earnMoney(amount)
                FortyFive.soundPlayer.situation("money_earned", screen)
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
            defaultButtonConfig()

            label("red wing", "Claim Rewards", Color.FortyWhite, 32) {
                touchable = Touchable.disabled
                syncDimensions()
            }

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

            label("red wing", "completed run:", Color.FortyWhite, 32) {
                syncDimensions()
            }
            actor(getSharedRunCard(run))
        }

        box {
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            relativeWidth(25f)
            relativeHeight(50f)

            label("red wing", "rewards:", Color.FortyWhite, 32) {
                syncDimensions()
            }
            if (cardsToExtract.isNotEmpty()) extractCardsReward()
            rewards(run.rewards)
        }
    }

    private fun CustomBox.rewards(rewards: List<RunReward>): Unit = rewards.forEach { reward ->
        when (reward) {
            is RunReward.Cash -> cashReward(reward)
        }
    }

    private fun CustomBox.extractCardsReward() {
        box {
            width = 400f
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            height = 70f
            backgroundHandle = "win_popup_item_card"

            image {
                backgroundHandle = "map_node_get_card"
                width = 40f
                height = 30f
                marginLeft = 10f
                marginRight = 10f
            }

            label("red wing", "You can keep cards!", Color.FortyWhite, 32) {
                syncDimensions()
            }
        }
        events.watchFor<ClaimRewardsEvent> { event -> event.append {
            later {
                val popupEvent = ExtractionPopup()
                events.fire(popupEvent)
                include(popupEvent.timeline!!)
            }
        } }
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

            label("red wing", "You get ${reward.amount}$", Color.FortyWhite, 32) {
                syncDimensions()
            }
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
    private class ExtractionPopup(var timeline: Timeline? = null)

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = WinRunScreen::class
    }

}
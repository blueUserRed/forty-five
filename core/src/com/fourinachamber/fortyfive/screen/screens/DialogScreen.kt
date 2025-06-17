package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.animation.AnimState
import com.fourinachamber.fortyfive.animation.xPositionAbstractProperty
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.map.events.dialog.AnimatedAdvancedTextWidget
import com.fourinachamber.fortyfive.map.events.dialog.DialogNpc
import com.fourinachamber.fortyfive.map.events.dialog.DialogScreenController
import com.fourinachamber.fortyfive.screen.ScreenManager
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.gameWidgets.TimelineController
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.PositionType
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.alpha
import kotlin.reflect.KClass

class DialogScreen : ScreenCreator() {

    override val name: String = "dialogScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"
    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)
    override val playAmbientSounds: Boolean = false
    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 100)

    private val events: EventPipeline = EventPipeline()

    private val timelines = TimelineController()

    private val dialogController: DialogScreenController by lazy {
        DialogScreenController(
            screen,
            events
        )
    }

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        npc(true)
        npc(false)

        textWidget()

        addDefaultOverlays(worldWidth, worldHeight, events, hasNavbar = false)
    }

    private fun CustomGroup.npc(isLeft: Boolean) {
        var currentNpc: DialogNpc? = null
        val animTime = 150

        image {

            val xAnimation = propertyAnimation(
                xPositionAbstractProperty(),
                AnimState("hidden", if (isLeft) -400f else worldWidth + 600f),
                AnimState("shown", if (isLeft) 0f else worldWidth - width),
                initialState = "hidden",
                defaultTime = animTime,
                defaultInterpolation = Interpolation.pow2
            )

            val alphaAnimation = propertyAnimation(
                this::alpha,
                AnimState("talking", 1f),
                AnimState("listening", 0.8f),
                initialState = "talking",
                defaultTime = 60,
                defaultInterpolation = Interpolation.linear
            )

            onLayoutAndNow {
                y = currentNpc?.offset?.y ?: 0f
            }

            events.watchFor<DialogScreenController.ChangeToNewDialogPart> { (part) ->
                val state = if (part.leftNpcTalking == isLeft) "talking" else "listening"
                alphaAnimation.state(state)
            }

            events.watchFor<DialogScreenController.ChangeNpcEvent> { (npc, eventAffectsLeft) ->
                if (isLeft != eventAffectsLeft) return@watchFor

                val timeline = Timeline.timeline {
                    action { xAnimation.state("hidden") }
                    delay(animTime)
                    action {
                        currentNpc = npc
                        backgroundHandle = npc?.textureName
                        invalidate()
                        println(npc)
                        npc ?: return@action
                        width = npc.width
                        height = npc.height
                        var x = if (isLeft) 0f else worldWidth - width
                        x += npc.offset.x
                        xAnimation.replaceState(AnimState("shown", x))
                        xAnimation.state("shown")
                    }
                }

                timelines.dispatchTimeline(timeline)
            }
        }
    }


    private fun CustomGroup.textWidget() {
        val advTextWidget = AnimatedAdvancedTextWidget(
            Triple("red_wing", Color.FortyWhite, 0.5f),
            screen,
            true
        )
        actor(advTextWidget) {
            relativeWidth(75F)
            relativeHeight(30F)
            centerX()
            y = -10F
            paddingTop = 50F
            paddingBottom = 50F
            paddingLeft = 80F
            paddingRight = 150F
            verticalTextAlign = CustomAlign.CENTER
            backgroundHandle = "dialog_background"

            box {
                positionType = PositionType.ABSOLUTE
                relativeWidth(100F)
                height = 40F
                y = parent.height - height
                horizontalAlign = CustomAlign.SPACE_AROUND
                flexDirection = FlexDirection.ROW
                minHorizontalDistBetweenElements = 350F
            }

            val continueButton = image {
                positionType = PositionType.ABSOLUTE
                backgroundHandle = "common_symbol_arrow_right"
                width = 40F
                height = 40F
                y = (parent.height - height) / 2
                x = parent.width - 100F
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                onInput(GameInputs.interact) {
                    if (!advTextWidget.isFinished) return@onInput
                    events.fire(DialogScreenController.NextClicked)
                }
            }

            events.watchFor<DialogScreenController.ChangeToNewDialogPart> { (part) ->
                continueButton.alpha = 0.5f
                advTextWidget.advancedText = part.text
            }

            onPartFinished {
                continueButton.alpha = 1f
            }

        }
    }


    override fun getScreenControllers(): List<ScreenController> = listOf(
        dialogController,
        BiomeBackgroundScreenController(screen, true),
        timelines
    )

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = DialogScreen::class
    }
}
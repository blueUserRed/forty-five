package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.xPositionAbstractProperty
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.Npc
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.commonComponents.AnimatedAdvancedTextWidget
import com.microwavestudios.fortyfive.screen.screenController.DialogScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.screenController.TimelineController
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.PositionType
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.*
import kotlin.reflect.KClass

class DialogScreen : ScreenCreator() {

    override val name: String = "dialogScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"
    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)
    override val playAmbientSounds: Boolean = false

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
    )

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
        choiceBox()

        addDefaultOverlays(
            worldWidth,
            worldHeight,
            events,
            hasNavbar = false,
            hasSettings = false,
            hasTutorial = false,
            hasTitleScreen = false
        )
    }

    private fun CustomGroup.choiceBox() = box {
        val optionGroup = "dialog-screen-choice-option"
        val optionModal = InputManager.Modal(listOf(optionGroup), screen)

        flexDirection = FlexDirection.COLUMN
        width = 240f
        height = 400f
        x = worldWidth / 2 - width / 2
//        x = worldWidth * (3.5f / 4f) - width
        y = worldHeight * 0.33f

        var currentPromise: Promise<String>? = null

        events.watchFor<DialogScreenController.Choice> { (choices, promise) ->
            currentPromise = promise
            choices.forEach { choice ->
                box(backgroundHints = arrayOf("dialog_answer_option", "dialog_answer_option_hover")) {
                    relativeWidth(100f)
                    syncHeight()
                    verticalAlign = CustomAlign.CENTER
                    horizontalAlign = CustomAlign.CENTER
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    backgroundHandle = "dialog_answer_option"
                    joinGroup(optionGroup)
                    observeInputState(
                        GameInputs.States.focused,
                        { backgroundHandle = "dialog_answer_option_hover" },
                        { backgroundHandle = "dialog_answer_option" },
                    )
                    onInput(GameInputs.interact) { currentPromise?.resolve(choice) }
                    label("roadgeek", choice, Color.FortyWhite, (24 * 1.1).toInt()) {
                        wrap = true
                        relativeWidth(90f)
                        syncHeight()
                    }
                    verticalSpacer(8f)
                }
            }
            optionModal.push()
            promise.then {
                clearChildren()
                optionModal.finished()
                currentPromise = null
            }
        }
    }

    private fun CustomGroup.nameLabels() {

        val left = label("red wing", "", fontSize = (32 * 0.9).toInt()) {
            backgroundHandle = "dialog_name_field"
            onLayoutAndNow {
                width = prefWidth * 1.3F
                height = prefHeight * 1.4F
            }
            setAlignment(Align.center)
            y = 300F
            x = 390F
            isVisible = false
        }

        val right = label("red wing", "", fontSize = (32 * 0.9).toInt()) {
            backgroundHandle = "dialog_name_field"
            setFontScale(0.9f)
            onLayoutAndNow {
                width = prefWidth * 1.3F
                height = prefHeight * 1.4F
            }
            setAlignment(Align.center)
            y = 300F
            x = 10F
            isVisible = false
        }

        events.watchFor<DialogScreenController.ChangeToNewDialogPart> { (part) ->
            if (part.leftNpcTalking) {
                left.isVisible = left.text.isNotBlank()
                right.isVisible = false
            } else {
                left.isVisible = false
                right.isVisible = right.text.isNotBlank()
            }
        }

        events.watchFor<DialogScreenController.ChangeNpcEvent> { (npc, isLeft) ->
            val label = if (isLeft) left else right
            label.setText(npc?.displayName)
        }
    }

    private fun CustomGroup.npc(isLeft: Boolean) {
        var currentNpc: Npc? = null
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
                y = currentNpc?.offsetY ?: 0f
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
                        backgroundHandle = npc?.texture
                        invalidate()
                        npc ?: return@action
                        width = npc.drawWidth
                        height = npc.drawHeight
                        var x = if (isLeft) 0f else worldWidth - width
                        x += npc.offsetX
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
            Triple("red wing", Color.FortyWhite, 16),
            screen,
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
                nameLabels()
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
package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.PropertyAnimation
import com.microwavestudios.fortyfive.animation.yPositionAbstractProperty
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.run.RunType
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.commonComponents.RunCardCreator.getSharedRunCard
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screens.MapScreen
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.Timeline

object RunBoardCreator {

    private val runBoardGroup: String = "run-board"

    fun ScreenCreator.getSharedRunBoard(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {

        val internalEvents = EventPipeline()

        val runBoard = runBoard(worldWidth, worldHeight, internalEvents)
        val popup = startRunPopup(worldWidth, worldHeight, internalEvents)
        val combined = newGroup {
            actor(runBoard)
            actor(popup)
        }

        val animation = PropertyAnimation(
            runBoard,
            runBoard.yPositionAbstractProperty(),
            Float::class,
            300,
            Interpolation.pow2,
            "close",
            states = arrayOf(
                AnimState("close", -worldHeight),
                AnimState("open", 0f)
            )
        )

        val modal = InputManager.Modal(listOf(runBoardGroup, NavbarCreator.navbarButtonGroup), screen)
        val filter = InputManager.FocusFilter(listOf(runBoardGroup), screen)
        filter.start()

        val openTimeline: () -> Timeline = { Timeline.timeline {
            includeAction(animation.stateAction("open"))
            action {
                modal.push()
                filter.end()
            }
        } }

        val closeTimeline: () -> Timeline = { Timeline.timeline {
            includeAction(animation.stateAction("close"))
            action {
                modal.finished()
                filter.start()
            }
        } }

        val navBarObject = NavbarCreator.NavBarObject(
            "Run Board",
            openTimeline,
            closeTimeline
        )

        return combined to navBarObject
    }

    private fun ScreenCreator.startRunPopup(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
    ): CustomGroup = newGroup {
        width = worldWidth
        height = worldHeight
        x = 0f
        y = 0f

        val buttonGroup = "runboard-popup-buttons"
        val modal = InputManager.Modal(listOf(buttonGroup), screen)
        val filter = InputManager.FocusFilter(listOf(buttonGroup), screen)
        filter.start()

        lateinit var promise: Promise<Boolean>

        isVisible = false

        lateinit var bodyLabel: NewLabel

        box {
            width = 600f
            height = 300f
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND

            backgroundHandle = "map_extraction_background"

            label("red wing", "Start run?", Color.FortyWhite, (32 * 1.3).toInt()) {
                setAlignment(Align.center)
            }
            bodyLabel = label("roadgeek", "", Color.FortyWhite, (28 * 0.8).toInt()) {
                setAlignment(Align.center)
            }

            box {
                relativeWidth(100f)
                height = 60f
                flexDirection = FlexDirection.ROW
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.SPACE_AROUND

                box(backgroundHints = buttonBackgroundHints()) {
                    horizontalAlign = CustomAlign.CENTER
                    verticalAlign = CustomAlign.CENTER
                    height = 60f
                    width = 140f
                    keyboardFocusable = KeyboardFocusable.LEAF
                    touchable = Touchable.enabled
                    joinGroup(buttonGroup)
                    defaultButtonConfig()

                    label("red wing", "Cancel", Color.FortyWhite, 32) {
                        touchable = Touchable.disabled
                        syncDimensions()
                    }

                    onInput(GameInputs.interact) {
                        promise.resolve(false)
                    }
                }
                box(backgroundHints = buttonBackgroundHints()) {
                    horizontalAlign = CustomAlign.CENTER
                    verticalAlign = CustomAlign.CENTER
                    height = 60f
                    width = 140f
                    keyboardFocusable = KeyboardFocusable.LEAF
                    touchable = Touchable.enabled
                    joinGroup(buttonGroup)
                    defaultButtonConfig()

                    label("red wing", "Start", Color.FortyWhite, 32) {
                        touchable = Touchable.disabled
                        syncDimensions()
                    }

                    onInput(GameInputs.interact) {
                        promise.resolve(true)
                    }
                }
            }
        }

        events.watchFor<ShowPopup> { event ->
            isVisible = true
            promise = event.result
            filter.end()
            modal.push()
            val profile = FortyFive.profileManager.currentProfile!!
            if (event.run.type == RunType.LIMITED) {
                bodyLabel.setText("")
            } else {
                bodyLabel.setText("You will take deck ${profile.currentCollectionDeck.name} with you")
            }
            event.result.then {
                isVisible = false
                filter.start()
                modal.finished()
            }
        }
    }

    private fun ScreenCreator.runBoard(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
    ): CustomGroup = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        touchable = Touchable.childrenOnly
        positionType = PositionType.ABSOLUTE

        val profile = FortyFive.profileManager.currentProfile ?: return@newGroup

        box {
            flexDirection = FlexDirection.COLUMN
            width = worldWidth * 0.85f
            height = worldHeight * 0.85f
            backgroundHandle = "microwave_studios_brown_texture"
            centerX()
            centerY()

            if (!profile.isRunActive) {
                noRunActiveBoard(this@runBoard, profile, events)
            } else box {
                relativeWidth(100f)
                relativeHeight(100f)
                flexDirection = FlexDirection.COLUMN
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER

                label("red wing", "Current Run:", fontSize = 32) {
                    syncDimensions()
                }
                actor(getSharedRunCard(profile.activeRun!!))
            }

        }
    }

    private fun CustomGroup.noRunActiveBoard(
        creator: ScreenCreator,
        profile: Profile,
        events: EventPipeline
    ) = with(creator) {

        val runBoard = profile.runBoardForArea(profile.currentAreaMap)

        fun runSelectCallback(run: Run): () -> Unit = {
            val event = ShowPopup(run)
            events.fire(event)
            event.result.then { startRun ->
                if (!startRun) return@then
                profile.startRun(run)
                FortyFive.screenManager.appendScreen(MapScreen)
                FortyFive.screenManager.screenFinished()
            }
        }

        fun runCard(run: Run, box: CustomBox) = box.actor(getSharedRunCard(run)) {
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            joinGroup(runBoardGroup)
            onInput(GameInputs.interact, runSelectCallback(run))
        }

        box {
            val box = this@box
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND
            relativeWidth(100f)
            relativeHeight(50f)
            runBoard.limitedRun?.let { runCard(it, box) }
            runBoard.constructedRun?.let { runCard(it, box) }
            runBoard.progressRun?.let { runCard(it, box) }
        }
        if (runBoard.specialRuns.isNotEmpty()) box {
            val box = this@box
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND
            relativeWidth(100f)
            relativeHeight(50f)
            runBoard.specialRuns.forEach { run -> runCard(run, box) }
        }

    }

    private data class ShowPopup(val run: Run, val result: Promise<Boolean> = Promise())
}
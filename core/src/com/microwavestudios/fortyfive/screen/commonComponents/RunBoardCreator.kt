package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.PropertyAnimation
import com.microwavestudios.fortyfive.animation.yPositionAbstractProperty
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.PositionType
import com.microwavestudios.fortyfive.screen.commonComponents.RunCardCreator.getSharedRunCard
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screens.MapScreen
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

object RunBoardCreator {

    fun ScreenCreator.getSharedRunBoard(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline
    ): Pair<CustomGroup, NavbarCreator.NavBarObject> {
        val group = runBoard(worldWidth, worldHeight)

        val animation = PropertyAnimation(
            group,
            group.yPositionAbstractProperty(),
            Float::class,
            300,
            Interpolation.pow2,
            "close",
            states = arrayOf(
                AnimState("close", -worldHeight),
                AnimState("open", 0f)
            )
        )

        val openTimeline: () -> Timeline = { Timeline.timeline {
            includeAction(animation.stateAction("open"))
        } }

        val closeTimeline: () -> Timeline = { Timeline.timeline {
            includeAction(animation.stateAction("close"))
        } }

        val navBarObject = NavbarCreator.NavBarObject(
            "Run Board",
            openTimeline,
            closeTimeline
        )

        return group to navBarObject
    }

    private fun ScreenCreator.runBoard(
        worldWidth: Float,
        worldHeight: Float
    ): CustomGroup = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        touchable = Touchable.childrenOnly
        positionType = PositionType.ABSOLUTE

        val profile = FortyFive.profileManager.currentProfile ?: return@newGroup
        val runBoard = profile.runBoardForArea(profile.currentAreaMap)

        box {
            flexDirection = FlexDirection.ROW
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.SPACE_AROUND
            width = worldWidth * 0.8f
            height = worldHeight * 0.65f
            backgroundHandle = "microwave_studios_brown_texture"
            centerX()
            centerY()

            fun runSelectCallback(run: Run): () -> Unit = {
                profile.startRun(run)
                FortyFive.screenManager.appendScreen(MapScreen)
                FortyFive.screenManager.screenFinished()
            }

            if (!profile.isRunActive) {
                actor(getSharedRunCard(runBoard.first)) {
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    onInput(GameInputs.interact, runSelectCallback(runBoard.first))
                }
                actor(getSharedRunCard(runBoard.second)) {
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    onInput(GameInputs.interact, runSelectCallback(runBoard.second))
                }
            } else box {
                relativeWidth(100f)
                relativeHeight(100f)
                flexDirection = FlexDirection.COLUMN
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER

                label("red_wing", "Current Run:")
                actor(getSharedRunCard(profile.activeRun!!))
            }

        }
    }

}
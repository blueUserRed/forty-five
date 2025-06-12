package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.InputManager
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.alpha

object PopupCreator {

    fun ScreenCreator.getSharedPopup(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline
    ): CustomGroup = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight
        touchable = Touchable.enabled

        group {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            touchable = Touchable.enabled
            backgroundHandle = "black_texture"
            alpha = 0.3f
        }

        isVisible = false

        val modal = InputManager.Modal(listOf("shared-popup-button"), screen)
        val filter = InputManager.FocusFilter(listOf("shared-popup-button"), screen)
        filter.start()

        events.watchFor<ShowPopup<*>> {
            isVisible = true
            filter.end()
            modal.push()
        }
        events.watchFor<PopupHidden> {
            isVisible = false
            modal.finished()
            filter.start()
        }

        box {
            backgroundHandle = "detail_widget_background_big"
            width = worldWidth * 0.5f
            syncHeight()
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER

            verticalSpacer(50f)

            val title = label("red_wing", "", Color.FortyWhite) {
                setFontScale(1.4f)
                setAlignment(Align.center)
                relativeWidth(90f)
                syncHeight()
            }

            verticalSpacer(20f)

            val body = label(
                "roadgeek",
                "",
                Color.FortyWhite
            ) {
                wrap = true
                relativeWidth(90f)
                setAlignment(Align.center)
                syncHeight()
            }

            events.watchFor<ShowPopup<*>> { popup ->
                title.setText(popup.title)
                body.setText(popup.body)
            }

            verticalSpacer(60f)

            box { buttonContainer(this@getSharedPopup, events) }
        }
    }

    private fun CustomBox.buttonContainer(creator: ScreenCreator, events: EventPipeline) = with(creator) {
        flexDirection = FlexDirection.ROW
        horizontalAlign = CustomAlign.SPACE_AROUND
        height = 50f
        logicalOffsetY = -15f
        relativeWidth(100f)
        events.watchFor<ShowPopup<*>> { event ->
            @Suppress("UNCHECKED_CAST")
            event as ShowPopup<Any?>
            clearChildren()
            event.options.forEach { (text, value) ->
                label("roadgeek", text, Color.FortyWhite, backgroundHints = buttonBackgroundHints()) {
                    height = 50f
                    width = 200f
                    setAlignment(Align.center)
                    setFontScale(0.9f)
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    joinGroup("shared-popup-button")
                    defaultButtonBackgrounds()
                    onInput(GameInputs.interact) {
                        event.callback(value)
                        events.fire(PopupHidden)
                    }
                }
            }
        }
    }

    private data object PopupHidden

    data class ShowPopup<T>(
        val title: String,
        val body: String,
        val options: List<Pair<String, T>>,
        val callback: (T) -> Unit
    )

}

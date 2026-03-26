package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.alpha

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
            onLayoutAndNow { height = prefHeight.coerceAtLeast(width * 0.5f) }
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_BETWEEN

            dropShadow = BakedDropShadow(
                "detail_widget_background_big",
                screen,
                0f, 0f,
                1.33f, 1.33f
            )

            box {
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                relativeWidth(100f)
                syncHeight()
                verticalSpacer(50f)
                val title = label("red wing", "", Color.FortyWhite, (32 * 1.4).toInt()) {
                    setAlignment(Align.center)
                    relativeWidth(90f)
                    syncHeight()
                }
                verticalSpacer(20f)
                val body = label(
                    "roadgeek",
                    "",
                    Color.FortyWhite,
                    28
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
            }

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
                label("roadgeek", text, Color.FortyWhite, 35, backgroundHints = buttonBackgroundHints()) {
                    height = 50f
                    width = 200f
                    setAlignment(Align.center)
                    touchable = Touchable.enabled
                    keyboardFocusable = KeyboardFocusable.LEAF
                    joinGroup("shared-popup-button")
                    defaultButtonConfig()
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

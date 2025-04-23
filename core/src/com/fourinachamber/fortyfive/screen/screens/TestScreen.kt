package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.GameInputs
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator

class TestScreen : ScreenCreator() {

    override val name: String = "test"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val background: String? = "background_bewitched_forest"

    override val transitionAwayTimes: Map<String, Int> = mapOf()

    override fun getRoot(): Group = newGroup {
        name("root")
        width = worldWidth
        height = worldHeight
        screen.inputManager.enableDragAndDrop("test-drag", "test-drop")

        box {
            name("container")
            flexDirection = FlexDirection.COLUMN
            width = worldWidth
            height = worldHeight
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.CENTER

            box {
                name("drops")
                height = 200f
                width = 900f
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_BETWEEN

                box {
                    name("drops-1")
                    width = 200f
                    height = 200f
                    backgroundHandle = "white_texture"
                    isDropTarget = true
                    touchable = Touchable.enabled
                    joinGroup("test-drop")
                    onDrop { backgroundHandle = "black_texture" }
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }

                box {
                    name("drops-2")
                    width = 200f
                    height = 200f
                    backgroundHandle = "white_texture"
                    isDropTarget = true
                    touchable = Touchable.enabled
                    joinGroup("test-drop")
                    onDrop { backgroundHandle = "black_texture" }
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }

                box {
                    name("drops-3")
                    width = 200f
                    height = 200f
                    backgroundHandle = "white_texture"
                    isDropTarget = true
                    touchable = Touchable.enabled
                    joinGroup("test-drop")
                    onDrop { backgroundHandle = "black_texture" }
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }
            }
            box {
                name("spacer")
                height = 200f
            }

            box {
                name("drags")
                width = 900f
                height = 200f
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_BETWEEN

                box {
                    name("drags-1")
                    width = 200f
                    height = 200f
                    backgroundHandle = "black_texture"
                    touchable = Touchable.enabled
                    isDraggable = true
                    joinGroup("test-drag")
                    keyboardFocusable = KeyboardFocusable.LEAF
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }

                box {
                    name("drags-2")
                    width = 200f
                    height = 200f
                    backgroundHandle = "black_texture"
                    touchable = Touchable.enabled
                    isDraggable = true
                    joinGroup("test-drag")
                    keyboardFocusable = KeyboardFocusable.LEAF
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }

                box {
                    name("drags-3")
                    width = 200f
                    height = 200f
                    backgroundHandle = "black_texture"
                    touchable = Touchable.enabled
                    isDraggable = true
                    joinGroup("test-drag")
                    keyboardFocusable = KeyboardFocusable.LEAF
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                }
            }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf()


}
package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.components.NavbarCreator
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.settingsKeyMap
import com.fourinachamber.fortyfive.screen.gameWidgets.TitleScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.Timeline
import ktx.actors.onClick

class TitleScreen : ScreenCreator() {

    override val name: String = "titleScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    val menuFocusGroup = "title_menu"

    val popupFocusGroup = "popup_group"
    val popupWidgetName = "popup_widget"

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 800 //800 fits good with the animation
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(menuFocusGroup)
                ),
            ),
            startGroups = listOf(menuFocusGroup),
        )
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(settingsKeyMap, screen)
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        TitleScreenController(screen)
    )

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "title_screen_background"
        }

        val blackOverlay = image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "transparent_black_texture"
            fixedZIndex = 100
            isVisible = false
            touchable = Touchable.enabled
        }

        for (i in 1..15) {
            addBullet("title_screen_bullet_$i")
        }
        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)


        box {
            debug = true
            x = 120F
            y = worldHeight * 0.65F
            addOption("Continue") { MapManager.changeToMapScreen() }
            addOption("Abandon Run") {}
            addOption("Reset Game") {}

            addOption("Settings") { handleSettings(blackOverlay, settingsObject) }
            addOption("View Credits") { MapManager.changeToCreditsScreen() }
            addOption("Quit") { handleQuit() }
        }

        actor(settings)
    }

    private fun CustomBox.handleQuit() {
        showPopup(
            "Do you want to quit?",
            "Are you sure you want to leave this game. Without you, the wild west will be unsafe and dangerous for all inhabitants, so choose wisely.",
            mapOf(
                "Quit" to { Gdx.app.exit() },
                "Cancel" to null
            )
        )
    }

    private fun CustomBox.handleSettings(
        blackOverlay: CustomImageActor,
        settingsObject: NavbarCreator.NavBarObject
    ) {
        blackOverlay.isVisible = true
        val controller = screen.screenControllers.filterIsInstance<TitleScreenController>().first()
        controller.timeline.appendAction(settingsObject.openTimelineCreator.invoke().asAction())
        blackOverlay.onClick {
            screen.escapeSelectionHierarchy()
        }
        controller.timeline.appendAction(Timeline.timeline {
            action {
                screen.curSelectionParent.onLeave = {
                    controller.timeline.appendAction(settingsObject.closeTimelineCreator.invoke().asAction())
                    blackOverlay.isVisible = false
                }
            }
        }.asAction())
    }


    fun Group.showPopup(title: String, description: String, actions: Map<String, (() -> Unit)?>) {
        var curParent = parent
        while (curParent.parent != null) curParent = curParent.parent
        curParent.box {
            screen.addNamedActor(popupWidgetName, this)
            name(popupWidgetName)
            positionType = PositionType.ABSOLUTE
            backgroundHandle = "detail_widget_background_big"
            width = worldWidth * 0.3f
            height = worldHeight * 0.3f
            x = (worldWidth - width) / 2
            y = (worldHeight - height) / 2
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_BETWEEN
            paddingTop = 25f
            paddingBottom = -20f
            debug = true
            label("red_wing", title, color = Color.FortyWhite) {
                setFontScale(1.4f)
                syncWidth()
            }
            advancedText("red_wing", Color.FortyWhite, 0.8f) {
                fitContentHeight = true
                setRawText(description, null)
                relativeWidth(80f)
            }

            box {
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_AROUND
                relativeWidth(100F)
                relativeHeight(20f)

                val labels = mutableListOf<CustomLabel>()
                actions.entries.forEach {
                    labels.add(label("red_wing", it.key) {
                        addButtonDefaults()
                        group = popupFocusGroup
                        onSelect {
                            it.value?.invoke()
                            if (it.value == null) removePopup()
                        }
                        onLayoutAndNow {
                            height = prefHeight * 1.2f
                            setAlignment(Align.center)
                        }
                        if (it.value == null) screen.focusSpecific(this)
                    })
                }

                val curMax = labels.maxOf { it.prefWidth } * 1.2f
                labels.forEach { it.width = curMax }
            }
        }

        screen.addToSelectionHierarchy(
            FocusableParent(listOf(SelectionTransition(groups = listOf(popupFocusGroup))),
                onLeave = {
                    removePopup()
                })
        )
    }

    fun removePopup() {
        val popup = screen.namedActorOrNull(popupWidgetName)
        if (popup != null) {
            screen.removeActorFromScreen(popup)
            screen.escapeSelectionHierarchy()
        }
    }


    fun Group.addOption(displayText: String, action: () -> Unit) = label("red_wing_bmp", displayText) {
        setFontScale(0.4f)
        syncWidth()
        syncHeight()
        group = menuFocusGroup
        setFocusableTo(true, this)
        isSelectable = true
        onSelect {
            screen.changeSelectionFor(this)
            action.invoke()
        }
        styles(
            resetEachTime = {
                underline = isFocused
            }
        )
    }

    fun Group.addBullet(name: String) = box {
        positionType = PositionType.ABSOLUTE
        width = worldWidth
        height = worldHeight
        name(name)
        backgroundHandle = name
    }
}
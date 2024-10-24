package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.events.dialog.AnimatedAdvancedTextWidget
import com.fourinachamber.fortyfive.map.events.dialog.DialogScreenController
import com.fourinachamber.fortyfive.map.events.dialog.DialogWidget
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.navbarFocusGroup
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.AdvancedTextWidget
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color

class DialogScreen : ScreenCreator() {
    override val name: String = "dialogScreen"
    val worldWidth = 1600f
    val worldHeight = 900f
    override val background: String = "background_bewitched_forest"
    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)
    override val playAmbientSounds: Boolean = false
    override val transitionAwayTimes: Map<String, Int> = mapOf("*" to 100)

    private val dialogWidgetName = "dialog_widget"
//    private val optionsParentName = "options_parent"

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight


//        image {
//            this.name(shopPersonWidgetName)
//            reportDimensionsWithScaling = true
//            fixedZIndex = 100
//            touchable = Touchable.disabled
//            onLayout {
//                val loadedDrawable1 = loadedDrawable ?: return@onLayout
//                width = loadedDrawable1.minWidth * scaleX
//                height = loadedDrawable1.minHeight * scaleY
//            }
//        }

        dialogWidget()

        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)
        actor(
            getSharedNavBar(
                worldWidth,
                worldHeight,
                listOf(settingsObject, settingsObject, settingsObject),
                screen
            )
        ) {
            onLayoutAndNow { y = worldHeight - height }
            centerX()
        }
        actor(settings)
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        DialogScreenController(screen, dialogWidgetName),
        BiomeBackgroundScreenController(screen, true)
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(), screen)
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(navbarFocusGroup)
                ),
            ),
            startGroups = listOf(navbarFocusGroup),
        )
    )

    private val getOption: (String) -> Actor = { text ->
        newGroup {
            width = 100f
            height = 50f
            debug = true
        }
    }

    private fun CustomGroup.dialogWidget() = actor(AnimatedAdvancedTextWidget(Triple("red_wing", Color.FortyWhite, 0.5f), screen, true)) {
        name(dialogWidgetName)
        width = 200F
        height = 200F
        debug = true
    }
}
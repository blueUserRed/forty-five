package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.events.dialog.AnimatedAdvancedTextWidget
import com.fourinachamber.fortyfive.map.events.dialog.DialogScreenController
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.navbarFocusGroup
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.TemplateStringLabel
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.PositionType
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
    private val optionsParentName = "options_parent"
    private val npcLeftImageWidgetName = "npc_left"
    private val npcRightImageWidgetName = "npc_right"
    private val continueWidgetName = "continue_widget"


    val dialogFocusGroup = "dialog_selection_group" //maybe add to companion object

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        npcImageWidgets(name = npcLeftImageWidgetName, offset = 130F)
        npcImageWidgets(name = npcRightImageWidgetName, offset = 980F)
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
        DialogScreenController(
            screen,
            dialogWidgetName,
            continueWidgetName,
            npcLeftImageWidgetName,
            npcRightImageWidgetName,
            optionsParentName,
        ),
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
                    groups = listOf(dialogFocusGroup, navbarFocusGroup)
                ),
            ),
            startGroups = listOf(navbarFocusGroup),
        )
    )

    private fun CustomGroup.npcImageWidgets(name: String, offset: Float) {
        box {
            relativeWidth(30F)
            debug = true
            x = offset
            verticalAlign = CustomAlign.END
            y = -1F
            height = 1F //this is needed, since otherwise the vertical align breaks
            image {
                this.name(name)
                this.relativeWidth(100F)
                this.height = worldHeight * 0.8F
                onLayoutAndNow { this.y = height }
            }
        }
    }

    private fun CustomGroup.npcNameWidget(templateString: String) {
        label(font = "red_wing", text = templateString, isTemplate = true) {
            backgroundHandle = "dialog_name_field"
            setFontScale(0.9f)
            onLayoutAndNow {
                setText((this as TemplateStringLabel).templateString.string)
                width = prefWidth * 1.3F
                height = prefHeight * 1.4F
                isVisible = text.isNotBlank() && (text.toString() != "{}")
            }
            setAlignment(Align.center)
            y = 300F
            x = 200F
        }
    }

    private fun CustomGroup.dialogWidget() =
        actor(AnimatedAdvancedTextWidget(Triple("red_wing", Color.FortyWhite, 0.5f), screen, true)) {
            name(dialogWidgetName)
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
            setFocusableTo(true, this@actor)
            isSelectable = true
            group = dialogFocusGroup

            box {
                positionType = PositionType.ABSOLUTE
                relativeWidth(100F)
                height = 40F
                y = parent.height - height
                horizontalAlign = CustomAlign.SPACE_AROUND
                flexDirection = FlexDirection.ROW
                minHorizontalDistBetweenElements = 350F
                npcNameWidget("{map.cur_event.person_left.displayName}")
                npcNameWidget("{map.cur_event.person_right.displayName}")
            }

            image {
                name(continueWidgetName)
                positionType = PositionType.ABSOLUTE
                backgroundHandle = "common_symbol_arrow_right"
                width = 40F
                height = 40F
                y = (parent.height - height) / 2
                x = parent.width - 100F
            }
        }
}
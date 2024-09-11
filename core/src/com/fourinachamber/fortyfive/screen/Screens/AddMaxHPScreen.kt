package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.events.heals.AddMaxHPScreenController
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.percent

class AddMaxHPScreen : ScreenCreator() {

    override val name: String = "addMaxHPScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 100
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(

        getHealOrMaxHPFocusableParent()
    )

    private fun getHealOrMaxHPFocusableParent(): FocusableParent {
        return FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf("addMaxHP_accept")
                ),
            ),
            startGroup = "addMaxHP_accept",
        )
    }

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(), screen)
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        AddMaxHPScreenController(screen),
        BiomeBackgroundScreenController(screen, true)
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
            backgroundHandle = "transparent_black_texture"
        }
        box {
            width = worldWidth.percent(38)
            height = worldHeight.percent(46)
            x = (worldWidth - width) / 2
            y = worldHeight.percent(27)
            backgroundHandle = "heal_or_max_background"

            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            wrap = CustomWrap.NONE

            paddingTop = worldHeight.percent(5)

            image {
                positionType = PositionType.ABSOLUTE
                width = worldHeight.percent(8.5)
                height = worldHeight.percent(8)
                y = parent.height - height / 2
                x = (parent.width - width) / 2
                backgroundHandle = "map_node_heal"
            }

            label("red_wing", "MAX HP Increased!", color = Color.FortyWhite)

            image {
                backgroundHandle = "heal_or_max_add_max_health"
                width = worldHeight.percent(17)
                height = worldHeight.percent(17)
                marginTop = 35F
                marginBottom = 25F
            }


            label("red_wing", "+{map.cur_event.max_hp.amount} MAX HP", isTemplate = true, color = Color.FortyWhite) {
                setFontScale(1.1F)
                setAlignment(Align.center)
            }

            label(
                "red_wing",
                "{stat.playerLives}/{stat.maxPlayerLives} HP -> {map.cur_event.max_hp.lives_new}/{map.cur_event.max_hp.maxLives_new} HP",
                isTemplate = true,
                color = Color.FortyWhite
            ) {
                setFontScale(0.5F)
                setAlignment(Align.center)
            }

            box {
                group = "addMaxHP_accept"
                backgroundHandle = "heal_or_max_accept_invalid"
                relativeWidth(28F)
                relativeHeight(13F)
                touchable = Touchable.enabled
                backgroundHandle = "heal_or_max_accept"
                setFocusableTo(true,this)
                isSelectable = true

                onFocusChange { _, _ ->
                    backgroundHandle = if (isFocused) {
                        "heal_or_max_accept_hover"
                    } else {
                        "heal_or_max_accept"
                    }
                }
                onSelect {
                    val controller =
                        screen.screenControllers.filterIsInstance<AddMaxHPScreenController>().firstOrNull()
                    controller
                        ?: throw RuntimeException("The AddMaxHPScreen needs a corresponding Controller to work")
                    controller.completed()
                }
            }
        }

        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)
        actor(getSharedNavBar(worldWidth, worldHeight, listOf(settingsObject, settingsObject, settingsObject), screen)) {
            onLayoutAndNow { y = worldHeight - height }
            centerX()
        }
        actor(settings)
    }
}
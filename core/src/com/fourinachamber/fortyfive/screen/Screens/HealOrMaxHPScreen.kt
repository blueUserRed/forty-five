package com.fourinachamber.fortyfive.screen.Screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransitionCondition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.map.events.heals.HealOrMaxHPScreenController
import com.fourinachamber.fortyfive.screen.DropShadow
import com.fourinachamber.fortyfive.screen.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.gameComponents.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.interpolate
import com.fourinachamber.fortyfive.utils.percent

class HealOrMaxHPScreen : ScreenCreator() {

    override val name: String = "healOrMaxHPScreen"

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
                    groups = listOf("healOrMaxHP_selection")
                ),
                SelectionTransition(
                    TransitionType.Seamless,
                    condition = SelectionTransitionCondition.Screenstate("healOrMaxHP_optionSelected"),
                    groups = listOf("healOrMaxHP_selection", "healOrMaxHP_accept")
                ),
            ),
            startGroup = "healOrMaxHP_selection",
        )
    }

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(), screen)
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        HealOrMaxHPScreenController(screen, "add_lives_option"),
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
            x = worldWidth.percent(26.5)
            y = worldHeight.percent(19)
            width = worldWidth.percent(48)
            height = worldHeight.percent(58)
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

            label("red_wing", "Choose your reward", color = Color.FortyWhite)

            chooseElementBox()

            val img = image {
                backgroundHandle = "forty_white_rounded"
                height = 1F
                logicalOffsetY = -10F
            }

            val text =
                label("red_wing", "{map.cur_event.max_hp.distanceToEnd}", isTemplate = true, color = Color.FortyWhite) {
                    setFontScale(0.6F)
                    setAlignment(Align.center)
                }
            text.onLayout { img.width = text.prefWidth * 1.5F }


            box {
                name("acceptButton")
                group = "healOrMaxHP_accept"
                backgroundHandle = "heal_or_max_accept_invalid"
                relativeWidth(23F)
                relativeHeight(10F)
                marginTop = parent.height.percent(6)
                touchable = Touchable.enabled
                screen.addOnScreenStateChangedListener { entered, state ->
                    if (state == "healOrMaxHP_optionSelected") {
                        if (entered) {
                            backgroundHandle = "heal_or_max_accept"
                            isFocusable = true
                            isDisabled = false
                            isSelectable = true
                        } else {
                            isDisabled = true
                            isFocusable = false
                            isSelectable = false
                            backgroundHandle = "heal_or_max_accept_invalid"
                        }
                    }
                }
                onFocusChange { _, _ ->
                    backgroundHandle = if (isFocused) {
                        "heal_or_max_accept_hover"
                    } else {
                        "heal_or_max_accept"
                    }
                }
                onSelect {
                    val controller =
                        screen.screenControllers.filterIsInstance<HealOrMaxHPScreenController>().firstOrNull()
                    controller
                        ?: throw RuntimeException("The HealOrMaxHPScreen needs a corresponding Controller to work")
                    controller.completed()
                }
            }
        }

        actor(getSharedNavBar(worldWidth)) {
            onLayoutAndNow { y = worldHeight - height }
            centerX()
        }
    }


    private fun Group.chooseElementBox() = box {
        relativeWidth(70F)
        relativeHeight(63F)
        flexDirection = FlexDirection.ROW
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.SPACE_BETWEEN

        chooseElementOption(
            "add_lives_option",
            "Heal {map.cur_event.heal.amount} HP",
            "{stat.playerLives}/{stat.maxPlayerLives} HP -> {map.cur_event.heal.lives_new}/{stat.maxPlayerLives} HP",
            "heal_or_max_add_health"
        )

        label("red_wing", "or", color = Color.FortyWhite) {
            setFontScale(1.3F)
            setAlignment(Align.center)
        }

        chooseElementOption(
            "add_max_hp_option",
            "+{map.cur_event.max_hp.amount} MAX HP",
            "{stat.playerLives}/{stat.maxPlayerLives} HP -> {map.cur_event.max_hp.lives_new}/{map.cur_event.max_hp.maxLives_new} HP",
            "heal_or_max_add_max_health"
        )
    }

    //This is basically "FromTemplate"
    private fun Group.chooseElementOption(
        name: String,
        templateMainText: String,
        templateSubText: String,
        textureName: ResourceHandle
    ) = box {
        name(name)
        relativeWidth(40F)
        relativeHeight(93F)
        flexDirection = FlexDirection.COLUMN
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.CENTER
        backgroundHandle = "heal_or_max_selector_background"
        touchable = Touchable.enabled
        isFocusable = true
        isSelectable = true
        group = "healOrMaxHP_selection"

        dropShadow = DropShadow(Color.Yellow, scaleY = 1f, showDropShadow = false)
        onSelectChange { _, new ->
            if (isSelected) {
                screen.deselectAllExcept(this)
                screen.enterState("healOrMaxHP_optionSelected")
            }
            if (new.isEmpty()) {
                screen.leaveState("healOrMaxHP_optionSelected")
            }
        }

        image {
            backgroundHandle = textureName
            width = worldHeight.percent(12)
            height = worldHeight.percent(12)
            marginTop = 40F
            marginBottom = 30F
        }

        label("red_wing", templateMainText, isTemplate = true, color = Color.DarkBrown) {
            setFontScale(1.1F)
            setAlignment(Align.center)
        }

        val img = image {
            backgroundHandle = "taupe_gray_rounded"
            height = 2F
            logicalOffsetY = -10F
        }
        val subtext = label("red_wing", templateSubText, isTemplate = true, color = Color.Taupe_gray) {
            setFontScale(0.5F)
            setAlignment(Align.center)
        }

        subtext.onLayout { img.width = subtext.prefWidth * 1.2F }

        styles(
            normal = {
                backgroundHandle = "heal_or_max_selector_background"
                dropShadow?.maxOpacity = 0.2f
                dropShadow?.showDropShadow = false
            },
            focused = {
                backgroundHandle = "heal_or_max_selector_background"
                dropShadow?.color = Color.FortyWhite
                dropShadow?.showDropShadow = true
                dropShadow?.maxOpacity = 0.2f
            },
            selected = {
                backgroundHandle = "heal_or_max_selector_background_selected"
                dropShadow?.color = Color.Yellow
                dropShadow?.showDropShadow = true
                dropShadow?.maxOpacity = 0.2f

            },
            selectedAndFocused = {
                backgroundHandle = "heal_or_max_selector_background_selected"
                dropShadow?.color = Color.FortyWhite.interpolate(Color.Yellow)
                dropShadow?.maxOpacity = 0.4f
                dropShadow?.showDropShadow = true
            }
        )
    }
}

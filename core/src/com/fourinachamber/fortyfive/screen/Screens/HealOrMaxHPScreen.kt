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
import com.fourinachamber.fortyfive.map.events.heals.HealOrMaxHPScreenController
import com.fourinachamber.fortyfive.screen.DropShadow
import com.fourinachamber.fortyfive.screen.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.gameComponents.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.percent

class HealOrMaxHPScreen : ScreenCreator() {

    override val name: String = "healOrMaxHPScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 1000
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(

        getHealOrMaxHPFocusableParent()
    )

    private fun getHealOrMaxHPFocusableParent(): FocusableParent {
        return FocusableParent(
            listOf(
                SelectionTransition(
                    SelectionTransition.TransitionType.SEAMLESS,
                    groups = listOf("healOrMaxHP_selection")
                ),
                SelectionTransition(
                    SelectionTransition.TransitionType.SEAMLESS,
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
                width = worldHeight.percent(8)
                height = worldHeight.percent(8)
                y = parent.height - width / 2
                x = (parent.width - width) / 2
                backgroundHandle = "map_node_heal"
            }

            label("red_wing", "Choose your reward", color = Color.FortyWhite)

            chooseElementBox()

            label("red_wing", "{map.cur_event.max_hp.distanceToEnd}", isTemplate = true, color = Color.FortyWhite) {
                setFontScale(0.6F)
                setAlignment(Align.center)
            }


            box {
                name("acceptButton")
                group = "healOrMaxHP_accept"
                backgroundHandle = "heal_or_max_accept_invalid"
                width = parent.width.percent(23)
                height = parent.height.percent(10)
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
//                        screen.updateSelectable()
                    }
                }
                onFocusChange { _, _ ->
                    if (isFocused) {
//                        this.dropShadow = DropShadow(Color.FortyWhite) //TODO this
                        backgroundHandle = "heal_or_max_accept_hover"

                    } else {
//                        this.dropShadow = null
                        backgroundHandle = "heal_or_max_accept"
                    }
                }
                onSelect {
                    val controller= screen.screenControllers.filterIsInstance<HealOrMaxHPScreenController>().firstOrNull()
                    controller ?: throw RuntimeException("The HealOrMaxHPScreen needs a corresponding Controller to work")
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
        width = parent.width.percent(70)
        height = parent.height.percent(63)
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
        width = parent.width.percent(40)
        height = parent.height.percent(93)
        flexDirection = FlexDirection.COLUMN
        verticalAlign = CustomAlign.CENTER
        horizontalAlign = CustomAlign.CENTER
        touchable = Touchable.enabled
        backgroundHandle = "heal_or_max_selector_background"
        isFocusable = true
        isSelectable = true
        touchable = Touchable.enabled
        group = "healOrMaxHP_selection"
        onFocusChange { _, _ ->
            if (isFocused) {
                this.dropShadow = DropShadow(Color.FortyWhite)
                debug = true
            } else {
                this.dropShadow = null
                debug = false
            }
        }
        onSelectChange { old, new ->
            if (isSelected) {
                backgroundHandle = "heal_or_max_selector_background_selected"
                FortyFive.mainThreadTask {
                    old.filter { it != this }.forEach { screen.deselectActor(it) }
                }
                screen.enterState("healOrMaxHP_optionSelected")
            } else {
                backgroundHandle = "heal_or_max_selector_background"
            }
            if (new.isEmpty()){
                screen.leaveState("healOrMaxHP_optionSelected")
            }
        }


        image {
            backgroundHandle = textureName
//            setScale(0.8F)
        }

        label("red_wing", templateMainText, isTemplate = true, color = Color.DarkBrown) {
            setFontScale(1.1F)
            setAlignment(Align.center)
        }


        image {
            backgroundHandle = "forty_white_rounded"
            setScale(300F, 5F)
        }


        label("red_wing", templateSubText, isTemplate = true, color = Color.Taupe_gray) {
            setFontScale(0.5F)
            setAlignment(Align.center)
        }
    }
}

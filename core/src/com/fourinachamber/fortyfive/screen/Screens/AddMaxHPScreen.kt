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
import com.fourinachamber.fortyfive.map.events.heals.AddMaxHPScreenController
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

class AddMaxHPScreen : ScreenCreator() {

    override val name: String = "addMaxHPScreen"

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

//            chooseElementBox()

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
//                marginTop = parent.height.percent(6)
                touchable = Touchable.enabled
                backgroundHandle = "heal_or_max_accept"
                isFocusable = true
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
        onFocusChange { _, _ ->
            updateDesignForElement()
        }
        onSelectChange { old, new ->
            updateDesignForElement()
            if (isSelected) {
                FortyFive.mainThreadTask {
                    old.filter { it != this }.forEach { screen.deselectActor(it) }
                }
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
    }

    private fun CustomBox.updateDesignForElement() {
        backgroundHandle = if (isSelected) {
            "heal_or_max_selector_background_selected"
        } else {
            "heal_or_max_selector_background"
        }
        dropShadow?.maxOpacity = 0.2f
        if (isFocused) {
            if (isSelected) {
                dropShadow?.color = Color.FortyWhite.interpolate(Color.Yellow)
                dropShadow?.maxOpacity = 0.4f
            } else {
                dropShadow?.color = Color.FortyWhite
            }
            dropShadow?.showDropShadow = true
        } else if (isSelected) {
            dropShadow?.color = Color.Yellow
            dropShadow?.showDropShadow = true
        } else {
            dropShadow?.showDropShadow = false
        }

    }
}

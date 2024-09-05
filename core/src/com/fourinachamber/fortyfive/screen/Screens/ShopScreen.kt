package com.fourinachamber.fortyfive.screen.Screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransitionCondition
import com.fourinachamber.fortyfive.map.events.heals.AddMaxHPScreenController
import com.fourinachamber.fortyfive.map.events.heals.HealOrMaxHPScreenController
import com.fourinachamber.fortyfive.map.events.shop.ShopScreenController
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

class ShopScreen : ScreenCreator() {

    override val name: String = "shopScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 100
    )

    private val messageWidgetName: String = "shop_messageWidget"
    private val cardsParentName: String = "shop_cardsParent"
    private val addToDeckWidgetName: String = "shop_addToDeck"
    private val addToBackpackWidgetName: String = "shop_addToBackpack"

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(

        getHealOrMaxHPFocusableParent()
    )

    private fun getHealOrMaxHPFocusableParent(): FocusableParent {
        return FocusableParent(
            listOf(
                SelectionTransition(
                    SelectionTransition.TransitionType.SEAMLESS,
                    groups = listOf("shop_leave")
                ),
            ),
            startGroup = "shop_leave",
        )
    }

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        KeyInputMap.createFromKotlin(listOf(), screen)
    )

    override fun getScreenControllers(): List<ScreenController> = listOf(
        ShopScreenController(screen, messageWidgetName, cardsParentName, addToDeckWidgetName, addToBackpackWidgetName),
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

        //TODO add to deck + add to backpack
        box {
            width = worldWidth.percent(63)
            height = worldHeight.percent(89)
            x = worldWidth.percent(36)
            y = 1f
            backgroundHandle = "shop_dark_background"
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND
            wrap = CustomWrap.NONE
            paddingTop = worldWidth.percent(3.25)
            paddingLeft = 50f

            val childrenSize = 87f

            box {
                debug = true
                flexDirection = FlexDirection.ROW
                relativeHeight(11.25f)
                relativeWidth(childrenSize)
                horizontalAlign = CustomAlign.SPACE_BETWEEN
                verticalAlign = CustomAlign.CENTER

                box { //name and icon
                    flexDirection = FlexDirection.ROW
                    relativeHeight(100F)

                    image {
                        backgroundHandle = "map_node_shop"
                        relativeHeight(100F)
                        onLayoutAndNow { width = height }
                    }
                    label(
                        "red_wing",
                        "{map.cur_event.personDisplayName}",
                        isTemplate = true,
                        color = Color.FortyWhite
                    ) {
                        setFontScale(2F)
                        setAlignment(Align.left)
                        syncDimensions()
                    }
                }

                box {// leave
                    backgroundHandle = "shop_back_button"
                    width = 230F
                    isFocusable = true
                    isSelectable = true
                    group = "shop_leave"
                    relativeHeight(60F)
                    onFocusChange { _, _ ->
                        backgroundHandle = if (isFocused) "shop_back_button_hover" else "shop_back_button"
                    }
                    onSelect { FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor("mapScreen")) }
                }
            }

            image { //line between
                backgroundHandle = "forty_white_rounded"
                relativeWidth(childrenSize)
                height = 2f
            }

            advancedText(
                "roadgeek",
                defaultColor = Color.FortyWhite,
                defaultFontScale = 0.8f,
            ) {//subtext
                name(messageWidgetName)
                this.relativeWidth(childrenSize)
                this.syncHeight()
                debug = true
                setRawText("My old test hihihihi", null)
            }

            box(isScrollable = true) {
                debug = true
                relativeWidth(childrenSize)
                relativeHeight(59f)
            }

            label(
                "red_wing",
                "drag to the merchant to confirm your purchase and add it to your backpack",
                color = Color.FortyWhite
            ) {
                setFontScale(0.7f)
//                setAlignment(Align.center)
                syncWidth()
            }
//            image {
//                positionType = PositionType.ABSOLUTE
//                width = worldHeight.percent(8.5)
//                height = worldHeight.percent(8)
//                y = parent.height - height / 2
//                x = (parent.width - width) / 2
//                backgroundHandle = "map_node_heal"
//            }
//
//            label("red_wing", "MAX HP Increased!", color = Color.FortyWhite)
//
////            chooseElementBox()
//
//            image {
//                backgroundHandle = "heal_or_max_add_max_health"
//                width = worldHeight.percent(17)
//                height = worldHeight.percent(17)
//                marginTop = 35F
//                marginBottom = 25F
//            }
//
//
//            label("red_wing", "+{map.cur_event.max_hp.amount} MAX HP", isTemplate = true, color = Color.FortyWhite) {
//                setFontScale(1.1F)
//                setAlignment(Align.center)
//            }
//
//            label(
//                "red_wing",
//                "{stat.playerLives}/{stat.maxPlayerLives} HP -> {map.cur_event.max_hp.lives_new}/{map.cur_event.max_hp.maxLives_new} HP",
//                isTemplate = true,
//                color = Color.FortyWhite
//            ) {
//                setFontScale(0.5F)
//                setAlignment(Align.center)
//            }
//
//            box {
//                group = "addMaxHP_accept"
//                backgroundHandle = "heal_or_max_accept_invalid"
//                relativeWidth(28F)
//                relativeHeight(13F)
////                marginTop = parent.height.percent(6)
//                touchable = Touchable.enabled
//                backgroundHandle = "heal_or_max_accept"
//                isFocusable = true
//                isSelectable = true
//
//                onFocusChange { _, _ ->
//                    backgroundHandle = if (isFocused) {
//                        "heal_or_max_accept_hover"
//                    } else {
//                        "heal_or_max_accept"
//                    }
//                }
//                onSelect {
//                    val controller =
//                        screen.screenControllers.filterIsInstance<AddMaxHPScreenController>().firstOrNull()
//                    controller
//                        ?: throw RuntimeException("The AddMaxHPScreen needs a corresponding Controller to work")
//                    controller.completed()
//                }
//            }
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
                    screen.deselectAllExcept(this)
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

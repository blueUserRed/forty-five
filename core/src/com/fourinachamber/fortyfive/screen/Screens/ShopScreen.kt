package com.fourinachamber.fortyfive.screen.Screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.map.events.shop.ShopScreenController
import com.fourinachamber.fortyfive.screen.DropShadow
import com.fourinachamber.fortyfive.screen.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.gameComponents.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.interpolate
import com.fourinachamber.fortyfive.utils.percent
import ktx.actors.onChange
import ktx.actors.onClick

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
                    groups = listOf("shop_leave", "shop_cards")
                ),
            ),
            startGroup = "shop_cards",
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
            backgroundHandle = "shop_background"
            flexDirection = FlexDirection.COLUMN
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.SPACE_AROUND
            wrap = CustomWrap.NONE
            paddingTop = worldWidth.percent(3)
            paddingLeft = 25f
            paddingRight = 10f

            val childrenSize = 88.5f

            textsAtTheTop(childrenSize)

            box(isScrollable = true) {
                this as CustomScrollableBox
                relativeWidth(childrenSize)
                relativeHeight(59f)
//                addScrollbarFromDefaults(CustomDirection.RIGHT,"backpack_scrollbar",null)
//                scrollDirectionStart = CustomDirection.RIGHT
                backgroundHandle = "shop_items_background"
                addRandomChildren()
                minVerticalDistBetweenElements = 15F
                minHorizontalDistBetweenElements = 15F
                scrollDistancePerScroll = 50F
                paddingLeft = 30f
//                paddingRight = 15f
                paddingTop = 15f
                paddingBottom = 30f
//                scrollDirectionStart = CustomDirection.BOTTOM
                addScrollbarFromDefaults(CustomDirection.RIGHT, "backpack_scrollbar", "backpack_scrollbar_background")
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
        }

        actor(getSharedNavBar(worldWidth)) {
            onLayoutAndNow { y = worldHeight - height }
            centerX()
        }
    }

    private fun Group.addRandomChildren() {


        fun CustomBox.addBasicStyles() {
            val size = 150f
            val listOf = listOf("shop_leave")
            width = size
            height = size
            targetGroups = listOf
            isDraggable = true
            onClick { println(name) }
            group = "shop_cards"
            dropShadow = DropShadow(Color.Green, showDropShadow = false)
            styles(
                normal = {
                    dropShadow?.showDropShadow = false
                },
                focused = {
                    dropShadow?.color = Color.Red
                    dropShadow?.showDropShadow = true
                },
                selected = {
                    dropShadow?.color = Color.Yellow
                    dropShadow?.showDropShadow = true
                },
                selectedAndFocused = {
                    dropShadow?.color = Color.Yellow.interpolate(Color.Red)
                    dropShadow?.showDropShadow = true
                }
            )
            bindDragging(this,screen)
        }
        box {
            backgroundHandle = "card%%leadersBullet"
            name("leadersBullet")
            addBasicStyles()
        }
        box {
            backgroundHandle = "card%%incendiaryBullet"
            name("incendiaryBullet")
            addBasicStyles()
        }
        for (i in 0 until 15) {
            box {
                backgroundHandle = "card%%bullet"
                name("bullet: $i")
                addBasicStyles()
            }
        }
    }

    private fun Group.textsAtTheTop(childrenSize: Float) = box {
        relativeWidth(childrenSize)
        syncHeight()
        box {
            height = 90F
            flexDirection = FlexDirection.ROW
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            verticalAlign = CustomAlign.CENTER
            relativeWidth(100F)
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
                width = 200F
                isFocusable = true
                isSelectable = true
                group = "shop_leave"
                relativeHeight(70F)
                onFocusChange { _, _ ->
                    backgroundHandle = if (isFocused) "shop_back_button_hover" else "shop_back_button"
                }
                onSelect { FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor("mapScreen")) }
            }
        }

        image { //line between
            backgroundHandle = "forty_white_rounded"
            relativeWidth(100f)
            height = 2f
            marginTop = 8f
            marginBottom = 12f
        }

        advancedText(
            "roadgeek",
            defaultColor = Color.FortyWhite,
            defaultFontScale = 0.8f,
        ) {//subtext
            name(messageWidgetName)
            relativeWidth(100f)
            syncHeight()
        }
    }
}

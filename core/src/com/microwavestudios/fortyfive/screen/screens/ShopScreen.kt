package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.map.events.shop.ShopScreenController
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.percent
import kotlin.reflect.KClass

class ShopScreen : ScreenCreator() {

    //TODO  children in CustomFocusableBox autoscroll + bar drag and drop
    //TODO check reroll

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
    private val shopPersonWidgetName: String = "shop_personWidget"
    private val rerollWidgetName: String = "shop_rerollWidget"

    private val dropTargetFilter: InputManager.FocusFilter by lazy {
        InputManager.FocusFilter(
            listOf(shopDropTargetGroup),
            screen
        )
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        ShopScreenController(
            screen,
            messageWidgetName,
            cardsParentName,
            addToDeckWidgetName,
            addToBackpackWidgetName,
            shopPersonWidgetName,
            rerollWidgetName,
        ),
        BiomeBackgroundScreenController(screen, true)
    )

    override fun getRoot(): Group = newGroup {
        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        dropTargetFilter.start()
        screen.inputManager.addDragAndDrop(ShopScreenController.availableCardGroup, shopDropTargetGroup)

        image {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            backgroundHandle = "transparent_black_texture"
        }

        dropTarget(worldHeight * 0.5F, "shop_add_to_deck", addToDeckWidgetName)
        dropTarget(worldHeight * 0.06F, "shop_add_to_backpack", addToBackpackWidgetName)

        box {
            width = worldWidth.percent(63)
            height = worldHeight.percent(89)
            x = worldWidth.percent(36)
            y = 1f
            backgroundHandle = "shop_background"
            flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.COLUMN
            horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
            verticalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.SPACE_AROUND
            wrap = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomWrap.NONE
            paddingTop = worldWidth.percent(3)
            paddingLeft = 25f
            paddingRight = 10f

            val childrenSize = 88.5f

            textsAtTheTop(childrenSize)

            box(isScrollable = true) {
                this as _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox
                relativeWidth(childrenSize)
                relativeHeight(59f)
                name(cardsParentName)
                backgroundHandle = "shop_items_background"
                minVerticalDistBetweenElements = 15F
                minHorizontalDistBetweenElements = 15F
                scrollDistancePerScroll = 50F
                paddingLeft = 30f
                paddingTop = 15f
                paddingBottom = 30f

                val cardFocusGrid = InputManager.FocusGrid()
                onLayout {
                    cardFocusGrid.clear()
                    var x = 0
                    var y = 0
                    walk().forEach { child ->
                        if (child !is CardActor) return@forEach
                        cardFocusGrid.set(x, y, child)
                        x++
                        if (x >= 4) {
                            x = 0
                            y++
                        }
                    }
                }
//                addTestChildren()
                addScrollbarFromDefaults(
                    _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomDirection.RIGHT,
                    "backpack_scrollbar",
                    "backpack_scrollbar_background"
                )
            }

            label(
                "red_wing",
                "drag to the merchant to confirm your purchase and add it to your backpack",
                color = Color.FortyWhite
            ) {
                setFontScale(0.7f)
                syncWidth()
            }

            label(
                "red_wing",
                "reroll Shop: {shop.currentRerollPrice}\$",
                isTemplate = true,
                color = Color.FortyWhite
            ) {
                name(rerollWidgetName)
                setFontScale(0.7f)
                width = 200F
                setAlignment(Align.center)
                positionType = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.PositionType.ABSOLUTE
                badTexture("reroll shop button", missingFocusTexture = true)
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                onInput(GameInputs.interact) {
                    screen.findController<ShopScreenController>()?.rerollShop()
                }
                onLayoutAndNow {
                    x = (parent.width - width) / 2
                    y = 70F
                }
            }
        }

        image {
            this.name(shopPersonWidgetName)
            reportDimensionsWithScaling = true
            fixedZIndex = 100
            touchable = Touchable.disabled
            onLayout {
                val loadedDrawable1 = loadedDrawable ?: return@onLayout
                width = loadedDrawable1.minWidth * scaleX * 0.35f
                height = loadedDrawable1.minHeight * scaleY * 0.35f
            }
        }

        addDefaultOverlays(worldHeight, worldHeight, EventPipeline())
    }

    private fun Group.addTestChildren() {
        fun _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomBox.addBasicStyles() {
            val size = 150f
            val listOf = listOf("shop_targets")
            width = size
            height = size

//            onFocus { if (!it) (parent as CustomScrollableBox).scrollTo(this) }
        }

        for (i in 0..20) {
            box {
                backgroundHandle = "card%%bullet"
                name("bullet_$i")
                addBasicStyles()
            }
        }
    }

    private fun Group.textsAtTheTop(childrenSize: Float) = box {
        relativeWidth(childrenSize)
        syncHeight()
        box {
            height = 90F
            flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.ROW
            horizontalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.SPACE_BETWEEN
            verticalAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
            relativeWidth(100F)
            box { //name and icon
                flexDirection = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.FlexDirection.ROW
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

            box(backgroundHints = arrayOf("shop_back_button_hover", "shop_back_button")) {// leave
                name("shop_back_button_name")
                backgroundHandle = "shop_back_button"
                width = 200F
                relativeHeight(70F)
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                observeInputState(
                    GameInputs.States.focused,
                    { backgroundHandle = "shop_back_button_hover" },
                    { backgroundHandle = "shop_back_button" }
                )
                onInput(GameInputs.interact) {
                    FortyFive.screenManager.screenFinished()
                }
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
            fitContentHeight = true
        }
    }


    private fun Group.dropTarget(yStart: Float, textureName: String, actorName: String) = image {

        name(actorName)
        relativeHeight(40F)
        relativeWidth(30F)
        y = yStart
        x = -width
        backgroundHandle = textureName
        fixedZIndex = 200
        val distanceNotSelected = -20F
        touchable = Touchable.disabled
        keyboardFocusable = KeyboardFocusable.LEAF

        isDropTarget = true
        joinGroup(shopDropTargetGroup)

        observeInputState(
            GameInputs.States.awaitingDrop,
            {
                dropTargetFilter.end()
                touchable = Touchable.enabled
            },
            {
                dropTargetFilter.start()
                touchable = Touchable.disabled
            },
        )

        fun updateState() {
            when {
                isInInputState(GameInputs.States.awaitingDropFocused) -> addAction(getAction(0f, y))
                isInInputState(GameInputs.States.awaitingDrop) -> addAction(getAction(distanceNotSelected, y))
                else -> addAction(getAction(-width, y))
            }
        }

        observeInputState(GameInputs.States.awaitingDrop, ::updateState, ::updateState)
        observeInputState(GameInputs.States.awaitingDropFocused, ::updateState, ::updateState)
    }

    private fun getAction(to: Float, y: Float) = MoveToAction().also {
        it.x = to
        it.y = y
        it.duration = 0.2f
        it.interpolation = Interpolation.pow2Out
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        const val shopDropTargetGroup = "shop-drop-target"

        override val creatorClass: KClass<out ScreenCreator> = ShopScreen::class
    }
}

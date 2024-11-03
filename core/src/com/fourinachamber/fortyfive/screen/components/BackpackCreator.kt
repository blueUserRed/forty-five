package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.keyInput.KeyInputCondition
import com.fourinachamber.fortyfive.keyInput.KeyInputMapEntry
import com.fourinachamber.fortyfive.keyInput.KeyPreset
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.map.statusbar.Backpack.Companion.LOG_TAG
import com.fourinachamber.fortyfive.screen.DropShadow
import com.fourinachamber.fortyfive.screen.general.CustomHorizontalGroup
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.CustomInputField
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.customActor.PropertyAction
import com.fourinachamber.fortyfive.screen.general.customActor.Selector
import com.fourinachamber.fortyfive.screen.general.customActor.Slider
import com.fourinachamber.fortyfive.screen.general.onSelect
import com.fourinachamber.fortyfive.screen.general.onSelectChange
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.ceil
import kotlin.plus

object BackpackCreator {
    private const val backpackFocusGroup = "backpack_defaultFocusGroup"
    private const val backpackOpenScreenState = "backpackIsOpen"

    fun ScreenCreator.getSharedBackpack(
        worldWidth: Float,
        worldHeight: Float
    ): Pair<CustomBox, NavbarCreator.NavBarObject> {

        val group = newBox {
            forcedPrefHeight = worldHeight * 0.85f
            horizontalAlign = CustomAlign.SPACE_BETWEEN
            fixedZIndex = NavbarCreator.navbarZIndex + 1
            flexDirection = FlexDirection.ROW
            debug = true
            forcedPrefWidth = worldWidth * 2f
            onLayout { x = (worldWidth - (forcedPrefWidth ?: 0F)) / 2 }
            syncDimensions()

            deck(this@getSharedBackpack, worldWidth)
            backpack(this@getSharedBackpack, worldWidth)
        }

        fun getAction(newWidth: Float) = PropertyAction(
            group,
            group::forcedPrefWidth,
            newWidth,
            group
        ).also {
            it.duration = 0.2f
            it.interpolation = Interpolation.exp5Out
        }

        val openTimelineCreator: () -> Timeline = {
            val action = getAction(worldWidth * 0.95f)
            Timeline.timeline {
                action {
                    group.addAction(action)
                    screen.enterState(backpackOpenScreenState)
                    screen.addToSelectionHierarchy(
                        FocusableParent(
                            listOf(
                                SelectionTransition(
                                    groups = listOf(
                                        NavbarCreator.navbarFocusGroup,
                                        backpackFocusGroup,
                                    )
                                ),
                            ),
                            maxSelectionMembers = 3 //TODO this
                        )
                    )
                }
                delayUntil { action.isComplete }
            }
        }

        val closeTimelineCreator: () -> Timeline = {
            val action = getAction(worldWidth * 2F)
            Timeline.timeline {
                action {
                    screen.leaveState(backpackOpenScreenState)
                    group.addAction(action)
                }
                delayUntil { action.isComplete }
            }
        }

        return group to NavbarCreator.NavBarObject(
            "Backpack",
            openTimelineCreator,
            closeTimelineCreator
        )
    }


    private fun Group.deck(creator: ScreenCreator, worldWidth: Float) = with(creator) {
        box {
            onLayoutAndNow { width = worldWidth * 0.49f }
            relativeHeight(100F)
            backgroundHandle = "backpack_deck_background"
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.END
            debug()

            box {
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.SPACE_AROUND
                verticalAlign = CustomAlign.CENTER
                relativeWidth(90f)
                relativeHeight(10f)
                debug()

                actor(textInputField(this@with)) {
                    relativeWidth(50f)
                    setFontScale(1.1f)
                    debug()
                }

                image {
                    backgroundHandle = "backpack_edit"
//                    backgroundHandle = "backpack_save" //TODO implement changing name of deck
                    width = 40f
                    height = width
                }

                box {
                    val deckSelectionParent = this
                    flexDirection = FlexDirection.ROW
                    fitContentInFlexDirection = true
                    relativeHeight(100f)
                    verticalAlign = CustomAlign.CENTER
                    minHorizontalDistBetweenElements = 1f
                    for (i in 1..5) {
                        image {
                            width = 55f
                            height = 50f
                            setFocusableTo(true, this@image)
                            isSelectable = true
                            dropShadow = DropShadow(Color.Magenta)
                            group = backpackFocusGroup
                            onSelect {
//                                screen.deselectActor(this)
                                changeDeckTo(i, deckSelectionParent)
                                isDisabled = true
                            }
                            styles(
                                resetEachTime = {
                                    dropShadow?.showDropShadow = false
                                },
                                normal = {
                                    backgroundHandle = "backpack_$i"
                                },
                                focused = {
                                    dropShadow?.showDropShadow = true
                                    backgroundHandle = "backpack_$i"
                                    toFront()
                                },
                                selected = {
                                    backgroundHandle = "backpack_${i}_hover"
                                },
                                selectedAndFocused = {
                                    toFront()
                                    dropShadow?.showDropShadow = true
                                    backgroundHandle = "backpack_${i}_hover"
                                },
                            )
                        }
                    }
                }
            }

            box(isScrollable = true) {
                flexDirection = FlexDirection.ROW
                minVerticalDistBetweenElements = 20f
                minHorizontalDistBetweenElements = 20f
                horizontalAlign = CustomAlign.SPACE_AROUND
                verticalAlign = CustomAlign.CENTER
                relativeWidth(90f)
                relativeHeight(80f)
                marginTop = parent.height * 0.04f
                debug()
            }
        }
    }

    private fun Group.backpack(creator: ScreenCreator, worldWidth: Float) = with(creator) {
        box {
            onLayoutAndNow { width = worldWidth * 0.49f }
            relativeHeight(100F)
            backgroundHandle = "backpack_backpack_background"
//            debug()
            paddingTop = 50f
        }
    }

    val backpackKeyMap = listOf<KeyInputMapEntry>(
        //TODO this
        KeyInputMapEntry(
            100,
//            KeyInputCondition.And(
//                KeyInputCondition.ScreenState(NavbarCreator.navbarOpenScreenState),
            KeyInputCondition.ScreenState(backpackOpenScreenState),
//            ),
            singleKeys = KeyPreset.LEFT.keys + KeyPreset.RIGHT.keys,
            { screen, keycode ->
                val par = screen.focusedActor ?: return@KeyInputMapEntry false
                if (par !is Layout || par !is CustomHorizontalGroup) return@KeyInputMapEntry false
                val selectionActor = par.children.filterIsInstance<Selector>().firstOrNull()
                val isLeft = KeyPreset.fromKeyCode(keycode) == KeyPreset.LEFT
                if (selectionActor != null) {
                    if (isLeft) selectionActor.onClick(0F)
                    else selectionActor.onClick(selectionActor.width)
                    return@KeyInputMapEntry true
                }
                val sliderActor = par.children.filterIsInstance<Slider>().firstOrNull()
                if (sliderActor != null) {
                    var oldPos = sliderActor.cursorPos
                    if (isLeft) sliderActor.updatePos((oldPos - 0.1F) * sliderActor.width)
                    else sliderActor.updatePos((oldPos + 0.1f) * sliderActor.width)
                    return@KeyInputMapEntry true
                }
                return@KeyInputMapEntry false
            }
        ),
        KeyInputMapEntry(
            100,
            KeyInputCondition.And(
                KeyInputCondition.ScreenState(NavbarCreator.navbarOpenScreenState),
                KeyInputCondition.ScreenState(backpackOpenScreenState)
            ),
            singleKeys = KeyPreset.ACTION.keys,
            { screen, keycode ->
                val par = screen.focusedActor ?: return@KeyInputMapEntry false
                if (par !is Layout || par !is CustomHorizontalGroup) return@KeyInputMapEntry false
                val selectionActor = par.children.filterIsInstance<Selector>().firstOrNull()
                if (selectionActor != null) {
                    selectionActor.onClick(selectionActor.width)
                    return@KeyInputMapEntry true
                }
                val sliderActor = par.children.filterIsInstance<Slider>().firstOrNull()
                if (sliderActor != null) {
                    if (sliderActor.cursorPos == 1F) sliderActor.updatePos(0F)
                    else {
                        sliderActor.updatePos(ceil((sliderActor.cursorPos + 0.01f) * 4) / 4F * sliderActor.width)
                    }
                    return@KeyInputMapEntry true
                }
                return@KeyInputMapEntry false
            }
        ),
    )


    private fun CustomBox.textInputField(creator: ScreenCreator): CustomInputField = with(creator) {
        val field = CustomInputField(
            screen,
            SaveState.curDeck.name,
            Label.LabelStyle(forceLoadFont("red_wing"), Color.FortyWhite),
        )
        return field
    }


    private fun changeDeckTo(newDeckId: Int, deckSelectionParent: CustomBox, firstInit: Boolean = false) {
        if (SaveState.curDeck.id == newDeckId && !firstInit) return
        if (!firstInit) FortyFiveLogger.log(
            FortyFiveLogger.LogLevel.DEBUG,
            LOG_TAG,
            "Changing Deck from ${SaveState.curDeck.id} to $newDeckId"
        )
        val oldActor = deckSelectionParent.originalChildren[SaveState.curDeck.id - 1] as CustomImageActor
        oldActor.screen.deselectActor(oldActor)
        oldActor.isDisabled = false
        SaveState.curDeckNbr = newDeckId
//            resetDeckNameField()
//            reloadDeck()
    }

}

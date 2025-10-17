package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomDirection
import com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox
import com.microwavestudios.fortyfive.screen.actors.CustomWrap
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

class Afterlife(val screen: OnjScreen, val gameEvents: EventPipeline) {

    private var actor: CustomBox? = null

    private var cards: MutableList<Card> = mutableListOf()

    private val afterlifeEvents: EventPipeline = EventPipeline()

    private var slots: List<CustomBox> = listOf()

    var isOpen: Boolean = false
        private set

    val isClosed: Boolean
        get() = !isOpen

    var afterlifeIsVisible: Boolean = false
        private set

    private val openFilter = InputManager.FocusFilter(listOf(afterliveSlotGroup), screen)

    init {
        openFilter.start()
    }

    fun getActor(creator: ScreenCreator): CustomBox {
        actor?.let { return it }
        with(creator) {
            val created = createActorWithReceiver()
            actor = created
            return created
        }
    }

    fun pushCard(card: Card) {
        cards.add(card)
        afterlifeEvents.fire(Events.CardPushed)
    }

    fun removeFirst() {

    }

    fun toggleTimeline(): Timeline = Timeline.timeline { later {
        include(if (isOpen) closeTimeline() else openTimeline())
    } }

    fun openTimeline(): Timeline = Timeline.timeline {
        val actor = actor ?: return@timeline
        val action = MoveToAction()
        action.x = -500f
        action.y = actor.y
        action.duration = 0.1f
        action.interpolation = Interpolation.pow2
        later {
            if (!afterlifeIsVisible) afterlifeEvents.fire(Events.MakeVisible)
            if (isOpen) return@later
            isOpen = true
            openFilter.end()
            action {
                afterlifeEvents.fire(Events.ChangeArrow(true))
                actor.addAction(action)
            }
            delayUntil { action.isComplete }
            action { actor.removeAction(action) }
        }
    }

    fun closeTimeline(): Timeline = Timeline.timeline {
        val actor = actor ?: return@timeline
        val action = MoveToAction()
        action.x = -1150f
        action.y = actor.y
        action.duration = 0.1f
        action.interpolation = Interpolation.pow2
        later {
            if (!isOpen) return@later
            isOpen = false
            openFilter.start()
            action {
                afterlifeEvents.fire(Events.ChangeArrow(false))
                actor.addAction(action)
            }
            delayUntil { action.isComplete }
            action { actor.removeAction(action) }
        }
    }


    private fun CustomBox.createSlots(creator: ScreenCreator) = with(creator) {
        val slots = mutableListOf<CustomBox>()

        box {
            relativeWidth(100f)
            relativeHeight(100f)
            flexDirection = FlexDirection.ROW_REVERSE
            horizontalAlign = CustomAlign.END
            verticalAlign = CustomAlign.CENTER

            box {
                marginRight = 30f
                width = 200f
                height = 200f
                backgroundHandle = "afterlife_bullet_affected"
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                marginBottom = 50f

                val card = cards.getOrNull(0)
                val firstSlot = box {
                    width = 150f
                    height = 150f
                    backgroundHandle = "afterlife_card_slot"
                    verticalAlign = CustomAlign.CENTER
                    horizontalAlign = CustomAlign.CENTER
                    marginBottom = 10f
                    joinGroup(afterliveSlotGroup)
                    keyboardFocusable = KeyboardFocusable.LEAF
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                    if (card == null) return@box
                    actor(card.actor) {
                        width = 120f
                        height = 120f
                    }
                }
                slots.add(firstSlot)
            }

            box(isScrollable = true) {
                this as CustomScrollableBox
                flexDirection = FlexDirection.ROW_REVERSE
                height = 200f
                width = 400f
                horizontalAlign = CustomAlign.END
                verticalAlign = CustomAlign.CENTER
                wrap = CustomWrap.NONE
                scrollDirectionStart = CustomDirection.RIGHT
                addScrollbarFromDefaults(
                    CustomDirection.BOTTOM,
                    "afterlife_scrollbar",
                    "afterlife_scrollbar_background",
                )
                repeat(cards.size.coerceAtLeast(4)) { index ->
                    val card = cards.getOrNull(index + 1)
                    val slot = box {
                        marginRight = 10f
                        width = 120f
                        height = 120f
                        backgroundHandle = "afterlife_card_slot"
                        verticalAlign = CustomAlign.CENTER
                        horizontalAlign = CustomAlign.CENTER
                        joinGroup(afterliveSlotGroup)
                        keyboardFocusable = KeyboardFocusable.LEAF
                        observeInputState(
                            GameInputs.States.focused,
                            { debug = true },
                            { debug = false }
                        )
                        if (card == null) return@box
                        actor(card.actor) {
                            width = 120f
                            height = 120f
                        }
                    }
                    slots.add(slot)
                }
            }
            layout()
        }
        this@Afterlife.slots = slots
    }

    private fun ScreenCreator.createActorWithReceiver(): CustomBox = newBox {
        backgroundHandle = "afterlife_background"
        badTexture("afterlife background", lowRes = true)
        height = 300f
        width = height * (898f / 210f)
        x = -1150f
        flexDirection = FlexDirection.ROW_REVERSE
        horizontalAlign = CustomAlign.END
        verticalAlign = CustomAlign.CENTER

        isVisible = false
        afterlifeIsVisible = false

        afterlifeEvents.watchFor<Events.MakeVisible> {
            isVisible = true
            afterlifeIsVisible = true
        }

        image(backgroundHints = arrayOf("afterlife_arrow_right", "afterlife_arrow_left")) {
            backgroundHandle = "afterlife_arrow_left"
            badTexture("afterlife arrow", lowRes = true, missingFocusTexture = true)
            afterlifeEvents.watchFor<Events.ChangeArrow> { (open) ->
                backgroundHandle = if (open) "afterlife_arrow_right" else "afterlife_arrow_left"
            }
            relativeHeight(40f)
            width = 60f
            marginRight = 30f
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled
            observeInputState(
                GameInputs.States.focused,
                { debug = true },
                { debug = false }
            )
            onInput(GameInputs.interact) {
                gameEvents.fire(GameControllerImpl.Events.AfterlifeOpenToggle)
            }
        }

        box {
            onLayoutAndNow { width = parent.width - 60f - 30f }
            relativeHeight(100f)
            flexDirection = FlexDirection.COLUMN
            label("red wing", "Afterlife", Color.FortyWhite, 32) {
                relativeWidth(100f)
                relativeHeight(20f)
                setAlignment(Align.center)
            }
            box {
                relativeWidth(100f)
                relativeHeight(80f)
                createSlots(this@createActorWithReceiver)
                afterlifeEvents.watchFor<Events.CardPushed> {
                    clearChildren()
                    invalidate()
                    createSlots(this@createActorWithReceiver)
                }
            }
        }
    }

    companion object {
        const val afterliveSlotGroup: String = "afterlive-slot"
    }

    private object Events {
        data class ChangeArrow(val open: Boolean)
        data object CardPushed
        data object MakeVisible
    }

}

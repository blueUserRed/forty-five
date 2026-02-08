package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomDirection
import com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox
import com.microwavestudios.fortyfive.screen.actors.CustomWrap
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.Timeline

class Afterlife(val screen: CustomScreen, val gameEvents: EventPipeline) {

    private var actor: CustomBox? = null

    private var _cards: MutableList<Card> = mutableListOf()
    val cards: List<Card>
        get() = _cards

    private val afterlifeEvents: EventPipeline = EventPipeline()

    var isOpen: Boolean = false
        private set

    val isClosed: Boolean
        get() = !isOpen

    var afterlifeIsVisible: Boolean = false
        private set

    private val openFilter = InputManager.FocusFilter(listOf(afterlifeSlotGroup), screen)

    private lateinit var slotParent: CustomScrollableBox

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
        _cards.add(card)
        afterlifeEvents.fire(Events.CardsChanged)
    }

    fun scrollToBeginTimeline(): Timeline = slotParent.scrollToBeginTimeline()

    fun popCardTimeline(): Timeline = Timeline.timeline {
        later {
            val duration = 0.3f
            val toRemove = _cards.firstOrNull()
            requireNotNull(toRemove) { "can't pop card, afterlife is empty" }

            _cards.getOrNull(1)?.let { slotParent.liftChild = it.actor.parent }
            _cards.forEachIndexed { index, card ->
                if (index == 0) return@forEachIndexed
                val cardBefore = _cards[index - 1]
                val xBefore = cardBefore.actor.localToStageCoordinates(Vector2(cardBefore.actor.x, cardBefore.actor.y)).x
                val thisX = card.actor.localToStageCoordinates(Vector2(card.actor.x, card.actor.y)).x
                var diff = xBefore - thisX
                if (index == 1) diff -= 15f
                val action = PropertyAction(
                    card.actor,
                    card.actor::drawOffsetX,
                    diff,
                )
                action.duration = duration
                action.interpolation = Interpolation.pow4
                card.actor.addAction(action)
            }
            delay((duration * 1000).toInt())
        }
        action {
            slotParent.liftChild = null
            _cards.forEach { card ->
                card.actor.drawOffsetX = 0f
                card.actor.clearActions()
            }
            _cards.removeFirst()
            afterlifeEvents.fire(Events.CardsChanged)
        }
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
                marginBottom = 40f

                box {
                    width = 150f
                    height = 150f
                    backgroundHandle = "afterlife_card_slot"
                    verticalAlign = CustomAlign.CENTER
                    horizontalAlign = CustomAlign.CENTER
                    marginBottom = 10f
                    joinGroup(afterlifeSlotGroup)
                    keyboardFocusable = KeyboardFocusable.LEAF
                    observeInputState(
                        GameInputs.States.focused,
                        { debug = true },
                        { debug = false }
                    )
                    afterlifeEvents.watchFor<Events.CardsChanged> {
                        clearChildren()
                        val card = _cards.getOrNull(0) ?: return@watchFor
                        actor(card.actor) {
                            width = 120f
                            height = 120f
                        }
                    }
                }
            }

            val scrollableBox = box(isScrollable = true) {
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
                var slotsCreated = 5
                repeat(slotsCreated - 1) { index -> createSlot(this@box, index) }
                invalidateHierarchy()
                afterlifeEvents.watchFor<Events.CardsChanged> {
                    val diff = _cards.size - slotsCreated
                    if (diff <= 0) return@watchFor
                    repeat(diff) { i -> createSlot(this@box, slotsCreated + i - 1) }
                    slotsCreated += diff
                }
            }
            slotParent = scrollableBox as CustomScrollableBox
            layout()
        }
    }

    private fun ScreenCreator.createSlot(parent: CustomBox, index: Int) = with(parent) {
        var card: Card?
        box {
            marginRight = 10f
            width = 120f
            height = 120f
            backgroundHandle = "afterlife_card_slot"
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.CENTER
            joinGroup(afterlifeSlotGroup)
            keyboardFocusable = KeyboardFocusable.LEAF
            observeInputState(
                GameInputs.States.focused,
                { debug = true },
                { debug = false }
            )
            afterlifeEvents.watchFor<Events.CardsChanged> {
                clearChildren()
                card = _cards.getOrNull(index + 1)
                if (card == null) return@watchFor
                actor(card!!.actor) {
                    width = 120f
                    height = 120f
                }
            }
            card = _cards.getOrNull(index + 1)
            if (card != null) actor(card!!.actor) {
                width = 120f
                height = 120f
            }
        }
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
            horizontalAlign = CustomAlign.END
            label("red wing", "Afterlife", Color.FortyWhite, 32) {
                width = 250f
                relativeHeight(20f)
                logicalOffsetX = -300f
                logicalOffsetY = -20f
                setAlignment(Align.center)
            }
            box {
                relativeWidth(100f)
                relativeHeight(80f)
                createSlots(this@createActorWithReceiver)
            }
        }
    }

    companion object {
        const val afterlifeSlotGroup: String = "afterlive-slot"
    }

    private object Events {
        data class ChangeArrow(val open: Boolean)
        data object CardsChanged
        data object MakeVisible
        data class DescendAnim(var timeline: Timeline? = null)
    }

}

package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.between
import ktx.actors.alpha
import ktx.actors.contains


/**
 * displays the cards in the hand
 * @param targetWidth the width this aims to be
 */
class CardHand(
    private val targetWidth: Float,
    private val cardSize: Float,
    private val opacityIfNotPlayable: Float,
    private val centerGap: Float,
    private val screen: OnjScreen,
) : WidgetGroup(), ZIndexActor, ZIndexGroup, StyledActor {

    override var fixedZIndex: Int = 0

    override var styleManager: StyleManager? = null

    override var isHoveredOver: Boolean = false

    /**
     * scaling applied to the card when hovered over
     */
    var hoveredCardScale = 1.0f

    /**
     * the spacing between the cards
     */
    var cardSpacing: Float = 0.0f

    /**
     * the z-index of any card in the hand that is not hovered over
     * will be startCardZIndicesAt + the number of the card
     */
    var startCardZIndicesAt: Int = 0

    /**
     * the z-index of a card that is hovered over
     */
    var hoveredCardZIndex: Int = 0

    /**
     * the z-index of the card-actors while dragged
     */
    var draggedCardZIndex: Int = 0

    /**
     * the cards currently in the hand
     */
    val cards: List<Card>
        get() = _cards

    private var _cards: MutableList<Card> = mutableListOf()

    private var currentHoverDetailActor: CardDetailActor? = null

    private lateinit var controller: GameController

    init {
        bindHoverStateListeners(this)
    }

    fun init(controller: GameController) {
        this.controller = controller
    }

    /**
     * adds a card to the hand
     */
    fun addCard(card: Card) {
        _cards.add(card)
        if (card.actor !in this) addActor(card.actor)
        updateCards()
        invalidateHierarchy()
    }

    /**
     * removes a card from the hand
     */
    fun removeCard(card: Card) {
        _cards.remove(card)
        removeActor(card.actor)
        updateCards()
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        updateCards()
    }

    private fun updateCards() {
    }

    override fun layout() {
        val cardsLeft = _cards.size / 2
        val spacePerSide = (width - centerGap) / 2f
        layoutSide(0, cardsLeft, -cardSize / 2, spacePerSide - cardSize, true)
        layoutSide(cardsLeft, _cards.size, spacePerSide + centerGap, width - cardSize / 2, false)
    }

    private fun layoutSide(fromIndex: Int, toIndex: Int, fromX: Float, toX: Float, reverseDirection: Boolean) {
        val sideWidth = toX - fromX
        val amountCards = toIndex - fromIndex
        val spacePerCard = sideWidth / amountCards
        var i = if (reverseDirection) toIndex - 1 else fromIndex
        var x = if (reverseDirection) toX else fromX
        while (
            (!reverseDirection && i < toIndex) ||
            (reverseDirection && i >= fromIndex)
        ) {
            val curCard = _cards[i]
            curCard.actor.setBounds(x, y, cardSize, cardSize)
            curCard.actor.fixedZIndex = if (reverseDirection) {
                startCardZIndicesAt + i
            } else {
                startCardZIndicesAt + fromIndex + (amountCards - (i - fromIndex))
            }
            if (reverseDirection) {
                x -= spacePerCard
                i--
            } else {
                x += spacePerCard
                i++
            }
        }
        resortZIndices()
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    override fun getPrefWidth(): Float {
        return targetWidth
    }

}

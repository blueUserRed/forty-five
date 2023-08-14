package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.between
import ktx.actors.contains
import kotlin.math.pow


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
    override var isClicked: Boolean=false

    /**
     * scaling applied to the card when hovered over
     */
    var hoveredCardScale = 1.0f

    /**
     * the spacing between the cards
     */
    var maxCardSpacing: Float = 0.0f

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
        _cards.add((_cards.size / 2), card)
        if (card.actor !in this) addActor(card.actor)
        invalidateHierarchy()
    }

    /**
     * removes a card from the hand
     */
    fun removeCard(card: Card) {
        _cards.remove(card)
        removeActor(card.actor)
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        updateCards()
    }

    private fun updateCards() {
        if (_cards.size == 0) return
        val cardsLeft = _cards.size / 2
        val spacePerSide = (width - centerGap) / 2f
        val zIndexChanged =
            layoutSide(0, cardsLeft, -cardSize / 2, spacePerSide - cardSize, true) ||
            layoutSide(cardsLeft, _cards.size, spacePerSide + centerGap, width - cardSize / 2, false)
        if (zIndexChanged) resortZIndices()
    }

    private fun layoutSide(fromIndex: Int, toIndex: Int, fromX: Float, toX: Float, reverseDirection: Boolean): Boolean {
        val sideWidth = toX - fromX
        val amountCards = toIndex - fromIndex
        val spacePerCard = (sideWidth / amountCards).coerceAtMost(maxCardSpacing)

        var zIndexChanged = false

        fun doZIndexFor(card: Card, zIndex: Int) {
            if (card.actor.fixedZIndex == zIndex) return
            card.actor.fixedZIndex = zIndex
            zIndexChanged = true
        }

        var i = if (reverseDirection) toIndex - 1 else fromIndex
        var x = if (reverseDirection) toX else fromX
        while (
            (!reverseDirection && i < toIndex) ||
            (reverseDirection && i >= fromIndex)
        ) {
            val card = _cards[i]

            if (card.actor.isDragged) {
                doZIndexFor(card, draggedCardZIndex)
                if (reverseDirection) {
                    x -= spacePerCard
                    i--
                } else {
                    x += spacePerCard
                    i++
                }
                continue
            }

            val hoveredOver = card.actor.isHoveredOver
            doZIndexFor(
                card,
                if (hoveredOver) {
                    hoveredCardZIndex
                } else if (reverseDirection) {
                    startCardZIndicesAt + i
                } else {
                    startCardZIndicesAt + fromIndex + (amountCards - (i - fromIndex))
                }
            )
            if (hoveredOver) {
                card.actor.setBounds(
                    x.between(0f, width - cardSize * hoveredCardScale),
                    cardHeightFunc(x).coerceAtLeast(0f),
                    cardSize * hoveredCardScale,
                    cardSize * hoveredCardScale
                )
            } else {
                card.actor.setBounds(x, cardHeightFunc(x), cardSize, cardSize)
            }
            card.actor.rotation = if (hoveredOver) 0f else cardHeightFuncDerivative(card.actor.x) * 50
            if (reverseDirection) {
                x -= spacePerCard
                i--
            } else {
                x += spacePerCard
                i++
            }
        }
        return zIndexChanged
    }

    private fun cardHeightFuncDerivative(x: Float): Float = 0.144f - 0.0018f * x

    private fun cardHeightFunc(x: Float): Float = -(0.03f * (x - 80f)).pow(2)

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

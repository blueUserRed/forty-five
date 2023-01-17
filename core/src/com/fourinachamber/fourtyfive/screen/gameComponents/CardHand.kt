package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ZIndexActor
import com.fourinachamber.fourtyfive.screen.general.ZIndexGroup
import com.fourinachamber.fourtyfive.utils.between
import ktx.actors.contains
import kotlin.math.min


/**
 * displays the cards in the hand
 * @param targetWidth the width this aims to be
 */
class CardHand(
    private val targetWidth: Float,
    detailFont: BitmapFont,
    detailFontColor: Color,
    detailBackground: Drawable,
    detailFontScale: Float,
    val detailOffset: Vector2,
    val detailWidth: Float
) : WidgetGroup(), ZIndexActor, ZIndexGroup {

    override var fixedZIndex: Int = 0

    /**
     * the scale of the card-Actors
     */
    var cardScale: Float = 1.0f

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
    private var currentWidth: Float = 0f
    private var isInitialized: Boolean = false

    private val onjScreen: OnjScreen
        get() = FourtyFive.curScreen!!

    private var hoverDetailActor: CustomLabel =
        CustomLabel("", Label.LabelStyle(detailFont, detailFontColor), detailBackground)

    init {
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
        hoverDetailActor.isVisible = false
        hoverDetailActor.fixedZIndex = Int.MAX_VALUE
        hoverDetailActor.wrap = true
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
     * removes a card from the hand (will not be removed from the stage)
     */
    fun removeCard(card: Card) {
        _cards.remove(card)
        removeActor(card.actor)
        updateCards()
        invalidateHierarchy()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        debug = true
        updateCards()
        super.draw(batch, parentAlpha)
        if (hoverDetailActor.isVisible) hoverDetailActor.draw(onjScreen.stage.batch, 1.0f)
    }

    private fun updateCards() { //TODO: split into layout and actual update

        if (_cards.isEmpty()) return

        val cardWidth = _cards[0].actor.width * cardScale

        var neededWidth = _cards.size * (cardSpacing + cardWidth) - cardSpacing
        this.currentWidth = neededWidth

        val xDistanceOffset = if (targetWidth < neededWidth) {
            -(neededWidth - targetWidth + cardWidth) / _cards.size
        } else 0f

        neededWidth = _cards.size * (xDistanceOffset + cardSpacing + cardWidth) - cardSpacing - xDistanceOffset

        var curX = if (targetWidth > neededWidth) {
            x + ((width - neededWidth) / 2)
        } else x

        curX -= width / 2

        val curY = y


        var isCardHoveredOver = false
        for (i in _cards.indices) {
            val card = _cards[i]
            card.actor.setScale(cardScale)
            if (!card.actor.isDragged) {
                card.actor.setPosition(curX, curY)
                card.actor.setScale(cardScale)

                if (card.actor.isHoveredOver) {
                    isCardHoveredOver = true
                    updateHoverDetailActor(card)
                    card.actor.setScale(hoveredCardScale)
                    card.actor.setPosition(
                        curX + ((card.actor.width * cardScale) - (card.actor.width * hoveredCardScale)) / 2,
                        curY + ((card.actor.height * cardScale) - (card.actor.height * hoveredCardScale)) / 2
                    )
                    card.actor.fixedZIndex = hoveredCardZIndex
                } else {
                    card.actor.fixedZIndex = startCardZIndicesAt + i
                }

            } else {
                card.actor.fixedZIndex = draggedCardZIndex
            }
            curX += cardWidth + cardSpacing + xDistanceOffset
        }

        hoverDetailActor.isVisible = isCardHoveredOver

        resortZIndices()

    }

    private fun updateHoverDetailActor(card: Card) {
        hoverDetailActor.setText(card.description)

        hoverDetailActor.width = detailWidth
        hoverDetailActor.height = hoverDetailActor.prefHeight

        val worldWidth = onjScreen.stage.viewport.worldWidth

        hoverDetailActor.setPosition(
            (card.actor.x + detailOffset.x - hoverDetailActor.width / 2 + (card.actor.width * cardScale) / 2)
                .between(0f, worldWidth - hoverDetailActor.width),
            card.actor.y + card.actor.height * cardScale + detailOffset.y
        )
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun getPrefWidth(): Float {
        return min(targetWidth, currentWidth)
    }

    override fun getMinWidth(): Float {
        return min(targetWidth, currentWidth)
    }

}

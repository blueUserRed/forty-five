package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.OnjArray
import onj.OnjObject

class Card(
    val name: String,
    val texture: TextureRegion,
    val description: String
) {

    val actor = CardActor(this)
    var isDraggable: Boolean = true

    companion object {

        const val cardTexturePrefix = "card%%"

        fun getFrom(cards: OnjArray, regions: Map<String, TextureRegion>): List<Card> = cards
            .value
            .map {
                it as OnjObject
                val name = it.get<String>("name")
                Card(
                    name,
                    regions["$cardTexturePrefix$name"] ?:
                        throw RuntimeException("cannot find texture for card $name"),
                    it.get<String>("description")
                )
            }

    }

}

/**
 * the actor representing a card
 */
class CardActor(val card: Card) : Image(card.texture), ZIndexActor {
    override var fixedZIndex: Int = 0

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fourtyfive.card.CardDragSource]
     */
    var isDragged: Boolean = false

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean = false
        private set

    init {
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
    }
}

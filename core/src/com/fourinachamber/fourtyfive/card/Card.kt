package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.fourinachamber.fourtyfive.screen.CustomImageActor
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.OnjArray
import onj.OnjNamedObject
import onj.OnjObject

/**
 * represents a card
 * @param name the name of the card
 * @param texture the texture for displaying the card
 * @param description the description of the card
 * @param type the CardType
 * @param baseDamage the base-damage of the card, before things like effects are applied
 */
class Card(
    val name: String,
    val texture: TextureRegion,
    val description: String,
    val type: Type,
    val baseDamage: Int,
    val coverValue: Int
) {

    /**
     * the actor for representing the card on the screen
     */
    val actor = CardActor(this)

    /**
     * true when the card can be dragged
     */
    var isDraggable: Boolean = true

    /**
     * true when [actor] is in an animation
     */
    var inAnimation: Boolean = false

    override fun toString(): String {
        return "card: $name"
    }

    companion object {

        /**
         * all textures of cards are prefixed with this string
         */
        const val cardTexturePrefix = "card%%"

        /**
         * gets an array of cards from an OnjArray
         */
        fun getFrom(cards: OnjArray, regions: Map<String, TextureRegion>): List<Card> = cards
            .value
            .map {
                it as OnjObject
                val name = it.get<String>("name")
                Card(
                    name,
                    regions["$cardTexturePrefix$name"] ?:
                        throw RuntimeException("cannot find texture for card $name"),
                    it.get<String>("description"),
                    when (val type = it.get<OnjNamedObject>("type").name) {
                        "Bullet" -> Type.BULLET
                        "Cover" -> Type.COVER
                        "OneShot" -> Type.ONE_SHOT
                        else -> throw RuntimeException("unknown Card type: $type")
                    },
                    it.get<Long>("baseDamage").toInt(),
                    it.get<Long>("coverValue").toInt()
                )
            }

    }

    enum class Type {
        BULLET, COVER, ONE_SHOT
    }

}

/**
 * the actor representing a card
 */
class CardActor(val card: Card) : CustomImageActor(card.texture), ZIndexActor {

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

package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.fourinachamber.fourtyfive.screen.CustomHorizontalGroup
import com.fourinachamber.fourtyfive.screen.CustomImageActor
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
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

    /**
     * if set to true, the preferred-, min-, and max-dimension functions will return the dimensions with the scaling
     * already applied
     */
    var reportDimensionsWithScaling: Boolean = false

    init {
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
    }

    override fun getMinWidth(): Float =
        if (reportDimensionsWithScaling) super.getMinWidth() * scaleX else super.getMinWidth()
    override fun getPrefWidth(): Float =
        if (reportDimensionsWithScaling) super.getPrefWidth() * scaleX else super.getPrefWidth()
    override fun getMaxWidth(): Float =
        if (reportDimensionsWithScaling) super.getMaxWidth() * scaleX else super.getMaxWidth()
    override fun getMinHeight(): Float =
        if (reportDimensionsWithScaling) super.getMinHeight() * scaleY else super.getMinHeight()
    override fun getPrefHeight(): Float =
        if (reportDimensionsWithScaling) super.getPrefHeight() * scaleY else super.getPrefHeight()
    override fun getMaxHeight(): Float =
        if (reportDimensionsWithScaling) super.getMaxHeight() * scaleY else super.getMaxHeight()
}

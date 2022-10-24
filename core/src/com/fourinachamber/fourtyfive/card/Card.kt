package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.fourinachamber.fourtyfive.game.CardActor
import onj.OnjArray
import onj.OnjObject

class Card(
    val name: String,
    val texture: TextureRegion
) {

    val actor = CardActor(this)

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
                        throw RuntimeException("cannot find texture for card $name")
                )
            }

    }

}
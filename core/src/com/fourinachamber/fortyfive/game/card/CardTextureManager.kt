package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.ServiceThreadMessage
import com.fourinachamber.fortyfive.utils.asPromise
import java.lang.RuntimeException

class CardTextureManager {

    private val cardTextures: MutableList<CardTextureData> = mutableListOf()

    fun init() {
    }

    fun cardTextureFor(card: Card, cost: Int, damage: Int): Promise<Texture> {
        val textureData = cardTextureDataFor(card)
        val isStandard = card.baseCost == cost && card.baseDamage == damage
        if (isStandard) {
            if (textureData.standardVariant == null) return drawStandardVariantFor(card, textureData)
            return textureData.standardVariant!!.texture.asPromise()
        } else {
            val variant = textureData.otherVariants.find { it.damage == damage && it.cost == cost }
            if (variant != null) return variant.texture.asPromise()
            return drawVariant(textureData, cost, damage)
        }
    }

    private fun drawStandardVariantFor(card: Card, textureData: CardTextureData): Promise<Texture> {
        val pixmap = Pixmap(textureData.cardPixmap.width, textureData.cardPixmap.height, Pixmap.Format.RGB888)
        val message = ServiceThreadMessage.DrawCardPixmap(
            pixmap,
            textureData.cardPixmap,
            card,
            card.baseDamage,
            card.baseCost,
            null
        )
        FortyFive.serviceThread.sendMessage(message)
        val promise = Promise<Texture>()
        message.promise.onResolve {
            FortyFive.mainThreadTask {
                val texture = Texture(pixmap)
                textureData.standardVariant = CardTextureVariant(
                    card.baseCost,
                    card.baseDamage,
                    pixmap,
                    texture
                )
                promise.resolve(texture)
            }
        }
        return promise
    }

    private fun cardTextureDataFor(card: Card): CardTextureData = cardTextures
        .find { it.cardName == card.name }
        ?: throw RuntimeException("no card with name ${card.name}")

    private data class CardTextureData(
        val cardName: String,
        var standardVariant: CardTextureVariant?,
        val otherVariants: List<CardTextureVariant>,
        val cardPixmap: Pixmap,
    )

    private data class CardTextureVariant(
        val cost: Int,
        val damage: Int,
        val pixmap: Pixmap,
        val texture: Texture
    )

}

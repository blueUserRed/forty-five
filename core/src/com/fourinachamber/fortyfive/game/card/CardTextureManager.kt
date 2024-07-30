package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.ServiceThreadMessage
import com.fourinachamber.fortyfive.utils.asPromise
import com.fourinachamber.fortyfive.utils.chain
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.RuntimeException

class CardTextureManager {

    private val cardTextures: MutableList<CardTextureData> = mutableListOf()

    fun init() {
        val cards = ConfigFileManager.getConfigFile("cards")
        cards
            .get<OnjArray>("cards")
            .value
            .map { card ->
                card as OnjObject
                CardTextureData(
                    card.get<String>("name"),
                    mutableListOf(),
                )
            }
            .forEach { cardTextures.add(it) }
    }

    fun cardTextureFor(card: Card, cost: Int, damage: Int): Promise<Texture> {
        val data = cardTextureDataFor(card)
        val variant = data.findVariant(cost, damage) ?: return createVariant(data, card, cost, damage)
        return variant.texture.asPromise()
    }

    private fun getCardPixmap(data: CardTextureData, card: Card): Promise<Pixmap> {
        val pixmap = data.cardPixmap
        pixmap?.let {
            data.cardPixmap = it
            return it.asPromise()
        }
        val message = ServiceThreadMessage.LoadCardPixmap(card.name)
        FortyFive.serviceThread.sendMessage(message)
        message.promise.onResolve { data.cardPixmap = it }
        return message.promise
    }

    private fun createVariant(
        data: CardTextureData,
        card: Card,
        cost: Int,
        damage: Int
    ): Promise<Texture> = getCardPixmap(data, card)
        .chain { cardPixmap ->
            val pixmap = Pixmap(cardPixmap.width, cardPixmap.height, Pixmap.Format.RGBA8888)
            val message = ServiceThreadMessage.DrawCardPixmap(
                pixmap,
                cardPixmap,
                card,
                damage,
                cost,
                null
            )
            FortyFive.serviceThread.sendMessage(message)
            message.promise
        }
        .chain { pixmap ->
            FortyFive.mainThreadTask {
                val texture = Texture(pixmap, true)
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
                val variant = CardTextureVariant(cost, damage, pixmap, texture)
                data.variants.add(variant)
                texture
            }
        }

    fun giveTextureBack(texture: Texture) {
    }

    private fun cardTextureDataFor(card: Card): CardTextureData = cardTextures
        .find { it.cardName == card.name }
        ?: throw RuntimeException("no card with name ${card.name}")

    private data class CardTextureData(
        val cardName: String,
        val variants: MutableList<CardTextureVariant>,
        var cardPixmap: Pixmap? = null,
    ) {

        fun findVariant(cost: Int, damage: Int): CardTextureVariant? =
            variants.find { it.cost == cost && it.damage == damage }
    }

    private data class CardTextureVariant(
        val cost: Int,
        val damage: Int,
        val pixmap: Pixmap,
        val texture: Texture,
    )

}

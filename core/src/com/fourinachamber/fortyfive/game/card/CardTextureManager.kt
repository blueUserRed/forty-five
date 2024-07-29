package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.ServiceThreadMessage
import com.fourinachamber.fortyfive.utils.asPromise
import com.fourinachamber.fortyfive.utils.chain
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.RuntimeException

class CardTextureManager {

    private val cardTextures: MutableList<CardTextureData> = mutableListOf()

    private val borrower = object : ResourceBorrower {}

    fun init() {
        val cards = ConfigFileManager.getConfigFile("cards")
        cards
            .get<OnjArray>("cards")
            .value
            .map { card ->
                card as OnjObject
                CardTextureData(
                    card.get<String>("name"),
                    null,
                    mutableListOf(),
                )
            }
            .forEach { cardTextures.add(it) }
    }

    fun cardTextureFor(card: Card, cost: Int, damage: Int): Promise<Texture> {
        val textureData = cardTextureDataFor(card)
        val isStandard = card.baseCost == cost && card.baseDamage == damage
        return if (isStandard) {
            if (textureData.standardVariant == null) {
                drawStandardVariantFor(card, textureData)
            } else {
                textureData.standardVariant!!.rc++
                textureData.standardVariant!!.texture.asPromise()
            }
        } else {
            val variant = textureData.otherVariants.find { it.damage == damage && it.cost == cost }
            if (variant != null) {
                variant.rc++
                variant.texture.asPromise()
            } else {
                drawVariant(card, textureData, cost, damage)
            }
        }
    }

    private fun drawVariant(
        card: Card,
        textureData: CardTextureData,
        cost: Int,
        damage: Int
    ): Promise<Texture> = ensureCardPixmap(textureData) { cardPixmap ->
        val pixmap = Pixmap(cardPixmap.width, cardPixmap.height, Pixmap.Format.RGB888)
        val message = ServiceThreadMessage.DrawCardPixmap(
            pixmap,
            cardPixmap,
            card,
            damage,
            cost,
            null
        )
        FortyFive.serviceThread.sendMessage(message)
        val promise = Promise<Texture>()
        message.promise.onResolve {
            FortyFive.mainThreadTask {
                val texture = Texture(pixmap)
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
                textureData.otherVariants.add(CardTextureVariant(cost, damage, pixmap, texture))
                promise.resolve(texture)
            }
        }
        return@ensureCardPixmap promise
    }

    private fun drawStandardVariantFor(
        card: Card,
        textureData: CardTextureData
    ): Promise<Texture> = ensureCardPixmap(textureData) { cardPixmap ->
        val pixmap = Pixmap(cardPixmap.width, cardPixmap.height, Pixmap.Format.RGB888)
        val message = ServiceThreadMessage.DrawCardPixmap(
            pixmap,
            cardPixmap,
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
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
                textureData.standardVariant = CardTextureVariant(
                    card.baseCost,
                    card.baseDamage,
                    pixmap,
                    texture
                )
                textureData.standardVariant!!.rc++
                promise.resolve(texture)
            }
        }
        return@ensureCardPixmap promise
    }

    private fun ensureCardPixmap(
        cardData: CardTextureData,
        callback: (Pixmap) -> Promise<Texture>
    ): Promise<Texture> {
        val cardPixmapPromise = cardData.cardPixmapPromise
        if (cardPixmapPromise?.isResolved == true) return callback(cardPixmapPromise.getOrError())
        if (cardPixmapPromise != null) return cardPixmapPromise.chain { callback(it) }
        val promise = Promise<Pixmap>()
        ResourceManager.borrow(borrower, Card.cardTexturePrefix + cardData.cardName)
        val texture = ResourceManager.get<Texture>(
            borrower,
            Card.cardTexturePrefix + cardData.cardName
        )
        if (!texture.textureData.isPrepared) texture.textureData.prepare()
        val texturePixmap = texture.textureData.consumePixmap()
        promise.resolve(texturePixmap)
        return promise.chain { callback(it) }
    }

    fun giveTextureBack(texture: Texture) {
        val standardVariantData = cardTextures.find { it.standardVariant?.texture === texture }
        val otherVariantData = cardTextures.find { texture in it.otherVariants.map { it.texture } }
        val variant = standardVariantData?.standardVariant
            ?: otherVariantData?.otherVariants?.find { it.texture === texture }
            ?: return
        val data = standardVariantData ?: otherVariantData ?: return
        variant.rc--
        if (variant.rc > 0) return
        variant.texture.dispose()
        variant.pixmap.dispose()
        if (otherVariantData != null) {
            data.otherVariants.remove(variant)
        } else {
            data.standardVariant = null
        }
    }

    private fun cardTextureDataFor(card: Card): CardTextureData = cardTextures
        .find { it.cardName == card.name }
        ?: throw RuntimeException("no card with name ${card.name}")

    private data class CardTextureData(
        val cardName: String,
        var standardVariant: CardTextureVariant?,
        val otherVariants: MutableList<CardTextureVariant>,
        var cardPixmapPromise: Promise<Pixmap>? = null,
    )

    private data class CardTextureVariant(
        val cost: Int,
        val damage: Int,
        val pixmap: Pixmap,
        val texture: Texture,
        var rc: Int = 0,
    )

}

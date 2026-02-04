package com.microwavestudios.fortyfive.game.card

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.RuntimeException

/**
 * loads, caches and manages textures for cards.
 *
 * The card textures in the
 * `assets/blobs/cards` directory only contain the art and the name, the damage and cost
 * information is missing. The CardTextureManager communicates with the [ServiceThread] to load
 * these textures and draw the missing information on them dynamically. This enables cost/damage
 * values to change mid-game.
 *
 * To obtain a card texture, use the [cardTextureFor] function and pass in the desired cost and
 * damage values. If the same texture is already used, a cached version will be returned.
 *
 * > **Important:**
 * > A texture obtained using [cardTextureFor] must be given back using [giveTextureBack] when
 * > it is no longer needed. Not doing that will result in memory leaks.
 *
 * > _Note:_
 * > If you want to draw a card to the screen, you shouldn't obtain the texture manually,
 * > but instead use add the [CardActor] to the scene.
 */
// TODO: make sure this isn't leaking memory somehow
class CardTextureManager {

    private val cardTextures: MutableList<CardTextureData> = mutableListOf()

    val statistics: Statistics = Statistics()

    fun init() {
        val cards = ConfigFileManager.loadCards(initializer = {})
        cards
            .map { card ->
                CardTextureData(
                    card.name,
                    mutableListOf(),
                    card.baseCost,
                    card.baseDamage,
                )
            }
            .forEach { cardTextures.add(it) }
    }

    fun cardTextureFor(card: Card, cost: Int, damage: Int, variablePostfix: String? = null): Promise<Texture> {
        statistics.lastLoadedCard = card.name
        val data = cardTextureDataFor(card)
        val variant = data.findVariant(cost, damage, variablePostfix) ?: run {
            return createVariant(data, card, cost, damage, variablePostfix)
        }
        variant.borrowers.add(card)
        statistics.cachedGets++
        return variant.texture
    }

    private fun getCardPixmap(data: CardTextureData, card: Card, variablePostfix: String?): Promise<Pixmap> {
        val pixmap = data.cardPixmap
        if (variablePostfix == null && pixmap != null) {
            data.cardPixmap = pixmap
            return pixmap.asPromise()
        }
        val message = ServiceThreadMessage.LoadCardPixmap(
            card.namespace,
            variablePostfix?.let { "${card.name}-$it" } ?: card.name
        )
        FortyFive.serviceThread.sendMessage(message)
        if (variablePostfix == null) message.promise.then { data.cardPixmap = it }
        statistics.pixmapLoads++
        return message.promise
    }

    private fun createVariant(
        data: CardTextureData,
        card: Card,
        cost: Int,
        damage: Int,
        variablePostfix: String?
    ): Promise<Texture> {
        val pixmapPromise = getCardPixmap(data, card, variablePostfix).chainMainThread { cardPixmap ->
            val padding = (cardPixmap.width * texturePaddingFraction).toInt()
            val pixmap = Pixmap(
                cardPixmap.width + 2 * padding,
                cardPixmap.height + 2 * padding,
                Pixmap.Format.RGBA8888
            )
            if (!card.actor.font.isResolved) FortyFive.resourceManager.forceResolve(card.actor.font)
            val message = ServiceThreadMessage.DrawCardPixmap(
                pixmap,
                cardPixmap,
                card,
                damage,
                cost,
                null,
                card.actor.font.getOrError()
            )
            FortyFive.serviceThread.sendMessage(message)
            message.promise
        }
        val texturePromise = pixmapPromise.chain { pixmap ->
            FortyFive.mainThreadTask {
                val texture = Texture(pixmap, true)
                texture.setFilter(
                    TextureFilter.MipMapLinearLinear,
                    TextureFilter.Linear
                )
                statistics.textureDraws++
                texture
            }
        }
        val variant = CardTextureVariant(cost, damage, pixmapPromise, texturePromise, mutableListOf(card), variablePostfix)
        data.variants.add(variant)
        return texturePromise
    }

    fun giveTextureBack(card: Card) {
        cardTextures.forEach { data ->
            val variant = data.variants.find { card in it.borrowers } ?: return@forEach
            variant.borrowers.remove(card)
            if (variant.borrowers.size > 0) return
            disposeVariant(variant, data)
        }
    }

    private fun disposeVariant(
        variant: CardTextureVariant,
        data: CardTextureData
    ) {
        val preventCompleteUnload = preventUnloadingOfCard(data)
        if (preventCompleteUnload && data.isStandardVariant(variant)) return
        if (variant.texture.isNotResolved) {
            variant.isDisposing = true
            variant.texture.thenMainThread { disposeVariant(variant, data) }
            return
        }
        data.variants.remove(variant)
        if (variant.variablePostFix != null) variant.pixmap.getOrError().dispose()
        variant.texture.getOrError().dispose()
        if (data.variants.isNotEmpty()) return
        if (preventCompleteUnload) return
        data.cardPixmap?.dispose()
        data.cardPixmap = null
    }

    private fun preventUnloadingOfCard(data: CardTextureData): Boolean =
        FortyFive.profileManager.currentProfile?.currentRunDeck?.cards?.let { data.cardName in it } ?: false

    private fun cardTextureDataFor(card: Card): CardTextureData = cardTextures
        .find { it.cardName == card.name }
        ?: throw RuntimeException("no card with name ${card.name}")

    private data class CardTextureData(
        val cardName: String,
        val variants: MutableList<CardTextureVariant>,
        val baseCost: Int,
        val baseDamage: Int,
        var cardPixmap: Pixmap? = null,
    ) {

        fun findVariant(cost: Int, damage: Int, variablePostFix: String?): CardTextureVariant? =
            variants.find { !it.isDisposing && it.cost == cost && it.damage == damage && it.variablePostFix == variablePostFix }

        fun isStandardVariant(variant: CardTextureVariant): Boolean =
            variant.variablePostFix == null && variant.cost == baseCost && variant.damage == baseDamage
    }

    private data class CardTextureVariant(
        val cost: Int,
        val damage: Int,
        val pixmap: Promise<Pixmap>,
        val texture: Promise<Texture>,
        val borrowers: MutableList<Card>,
        val variablePostFix: String?,
        var isDisposing: Boolean = false,
    )

    inner class Statistics {

        var cachedGets: Int = 0
        var textureDraws: Int = 0
        var pixmapLoads: Int = 0

        var lastLoadedCard: String? = null

        val loadedTextures: Int
            get() = cardTextures.flatMap { it.variants }.count()

        val textureUsages: Int
            get() = cardTextures.flatMap { it.variants }.sumOf { it.borrowers.size }

    }

    companion object {
        const val texturePaddingFraction: Double = 1.0 / 75.0
    }

}

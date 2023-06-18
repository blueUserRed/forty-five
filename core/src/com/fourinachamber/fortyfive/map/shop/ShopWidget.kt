package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.screen.general.*
import ktx.actors.alpha
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.random.Random

class ShopWidget(
    texture: String,
    dataFile: String,
    val dataFont: Label.LabelStyle,
    val dataDragBehaviour: OnjNamedObject,
    val maxPerLine: Int,
    val widthPercentagePerItem: Float,
    val screen: OnjScreen,
) : CustomFlexBox(screen) {

    private val _cards: MutableList<Card> = mutableListOf()
    private val priceTags: MutableList<CustomLabel> = mutableListOf()
    private val allCards: MutableList<Card> = mutableListOf()
    private lateinit var boughtIndices: MutableList<Int>

    private lateinit var dragAndDrop: DragAndDrop
    /*override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }*/

    private val cardPrototypes: MutableList<CardPrototype>

    init {
        backgroundHandle = texture
        val onj = OnjParser.parseFile(dataFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screen, ::initCard).toMutableList()
    }


    fun addItems(
        seed: Long,
        boughtIndices: MutableList<Int>,
        dragAndDrop: DragAndDrop
    ) {
        this.boughtIndices = boughtIndices
        this.dragAndDrop = dragAndDrop
        boughtIndices.add(1)
        val rnd = Random(seed)
        val nbrOfItems = (6)
        for (i in 0 until nbrOfItems) {
            val cardId = (0..cardPrototypes.size).random(rnd)
            _cards.add(cardPrototypes[cardId].create())
            val stringLabel = CustomLabel(screen, "${_cards.last().cost}$", dataFont, false)
            stringLabel.setFontScale(0.1F)
            stringLabel.setAlignment(Align.center)
            priceTags.add(stringLabel)
            if (i in boughtIndices) buyCard(i)
            add(stringLabel)
            add(_cards.last().actor)
        }
    }

    private fun buyCard(
        i: Int,
    ) {
        _cards[i].isDraggable = false
        _cards[i].actor.alpha = 0.5F
        priceTags[i].text.clear()
        priceTags[i].text.append("out of stock")
        if (i !in boughtIndices) boughtIndices.add(i)
    }

    override fun layout() {
        super.layout()
        val distanceBetweenX = width * ((100 - (maxPerLine * widthPercentagePerItem)) / (maxPerLine + 1) / 100)
        val sizePerItem = width * widthPercentagePerItem / 100
        val distanceBetweenY = (height - 2 * sizePerItem) / 2.5F
        for (i in 0 until _cards.size) {
            val card = _cards[i]
            card.actor.width = sizePerItem
            card.actor.height = sizePerItem
            card.actor.x = distanceBetweenX * (i % maxPerLine + 1) + sizePerItem * (i % maxPerLine)
            card.actor.y = height - distanceBetweenY * (i / maxPerLine + 0.5F) - sizePerItem * (i / maxPerLine + 1)

            val label = priceTags[i]
            label.setBounds(
                card.actor.x,
                card.actor.y - distanceBetweenY * 2 / 4,
                sizePerItem,
                distanceBetweenY * 2 / 4
            );
        }
    }

    private fun initCard(card: Card) {
        val behaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
            dataDragBehaviour.name,
            dragAndDrop,
            card.actor,
            dataDragBehaviour
        )
        dragAndDrop.addSource(behaviour)
        allCards.add(card)
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }
    }

}

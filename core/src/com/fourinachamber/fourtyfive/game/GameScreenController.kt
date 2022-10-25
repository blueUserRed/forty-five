package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.DragAndDropBehaviourFactory
import com.fourinachamber.fourtyfive.screen.ScreenController
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import onj.*


/**
 * the Controller for the main game screen
 */
class GameScreenController(onj: OnjNamedObject) : ScreenController() {

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardAtlasFile = onj.get<String>("cardAtlasFile")
    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragAndDropBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private var curScreen: ScreenDataProvider? = null

    private var cardHand: CardHand? = null
    private var revolver: Revolver? = null

    private var cards: List<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    override fun init(screenDataProvider: ScreenDataProvider) {
        curScreen = screenDataProvider
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cardAtlas = TextureAtlas(Gdx.files.internal(cardAtlasFile))

        for (region in cardAtlas.regions) {
            screenDataProvider.addTexture("${Card.cardTexturePrefix}${region.name}", region)
        }

        for (texture in cardAtlas.textures) screenDataProvider.addDisposable(texture)
        cards = Card.getFrom(onj.get<OnjArray>("cards"), screenDataProvider.textures)

        for (card in cards) {
            val behaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
                cardDragAndDropBehaviour.name,
                cardDragAndDrop,
                screenDataProvider,
                card.actor,
                cardDragAndDropBehaviour
            )
            cardDragAndDrop.addSource(behaviour)
        }

        initCardHand()
        initRevolver()
    }

    private fun initCardHand() {
        val curScreen = curScreen!!

        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActors[cardHandName]
            ?: throw RuntimeException("no named actor with name $cardHandName")
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        this.cardHand = cardHand

        cardHand.cardScale = cardHandOnj.get<Double>("cardScaling").toFloat()
        cardHand.cardSpacing = cardHandOnj.get<Double>("cardSpacing").toFloat()
        cardHand.cardZIndex = cardHandOnj.get<Long>("cardZIndex").toInt()
        cardHand.draggedCardZIndex = cardHandOnj.get<Long>("draggedCardZIndex").toInt()
        cardHand.debug = true

        cardHand.addCard(cards[0])
        cardHand.addCard(cards[1])
        cardHand.addCard(cards[2])
    }

    private fun initRevolver() {
        val curScreen = curScreen!!

        val revolverName = revolverOnj.get<String>("actorName")
        val revolver = curScreen.namedActors[revolverName]
            ?: throw RuntimeException("no named actor with name $revolverName")
        if (revolver !is Revolver) throw RuntimeException("actor named $revolverName must be a Revolver")
        this.revolver = revolver
    }

    override fun end() {
        curScreen = null
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }
}

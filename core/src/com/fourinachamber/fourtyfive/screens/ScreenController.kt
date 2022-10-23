package com.fourinachamber.fourtyfive.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.cards.Card
import onj.*

object ScreenControllerFactory {

    private val controllers: MutableMap<String, (OnjNamedObject) -> ScreenController> = mutableMapOf(
        "GameScreenController" to { onj -> GameScreenController(onj) }
    )

    /**
     * will return an instance of the ScreenController with name [name]
     * @throws RuntimeException when no controller with that name exists
     * @param onj the onjObject containing the configuration of the controller
     */
    fun controllerOrError(name: String, onj: OnjNamedObject): ScreenController {
        val behaviourCreator = controllers[name] ?: throw RuntimeException("Unknown behaviour: $name")
        return behaviourCreator(onj)
    }
}

/**
 * ScreenControllers can be used to add advanced functionality to a screen
 */
abstract class ScreenController {

    /**
     * called when this is set as a controller for a screen
     */
    open fun init(screenDataProvider: ScreenDataProvider) { }

    /**
     * called before the controller is changed to different one
     */
    open fun end() { }

}

/**
 * the Controller for the main game screen
 */
class GameScreenController(onj: OnjNamedObject) : ScreenController() {

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardAtlasFile = onj.get<String>("cardAtlasFile")
    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragAndDropBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private var curScreen: ScreenDataProvider? = null
    private var cardHand: CardHand? = null
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
        cardHand.debug = true

        cardHand.addCard(cards[0])
        cardHand.addCard(cards[1])
        cardHand.addCard(cards[2])
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

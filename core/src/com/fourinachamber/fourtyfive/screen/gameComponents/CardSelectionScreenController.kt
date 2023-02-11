package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.SaveState
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.game.card.CardPrototype
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.between
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import dev.lyze.flexbox.FlexBox
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.min

/**
 * controls a screen that allows selecting cards
 */
class CardSelectionScreenController(private val onj: OnjNamedObject) : ScreenController() {

    private val cardSelectionActorName = onj.get<String>("cardSelectionActorName")
    private val nextScreen = onj.get<String>("nextScreen")
    private val cardConfigFile = onj.get<String>("cardConfigFile")
    private val cardsToSelect = onj.get<Long>("cardsToSelect").toInt()
    private val cardScale = onj.get<Double>("cardScale").toFloat()

    private val cardBehaviourOnj = onj.get<OnjNamedObject>("cardBehaviour")

    private lateinit var cardSelectionActor: WidgetGroup
    private var cardPrototypes: List<CardPrototype> = listOf()
    private val cards: MutableList<Card> = mutableListOf()
    private lateinit var onjScreen: OnjScreen

    private var emptyText = onj.get<String>("emptyText")
    private lateinit var emptyFont: BitmapFont
    private var emptyFontColor = onj.get<Color>("emptyFontColor")
    private var emptyFontScale = onj.get<Double>("emptyFontScale").toFloat()

    private var currentHoverDetail: CardDetailActor? = null

    override fun init(onjScreen: OnjScreen) {
        this.onjScreen = onjScreen

        emptyFont = ResourceManager.get(onjScreen, onj.get<String>("emptyFont"))

        val cardSelectionActor = onjScreen.namedActorOrError(cardSelectionActorName)
        if (cardSelectionActor !is WidgetGroup) {
            throw RuntimeException("actor with name $cardSelectionActorName must be a WidgetGroup!")
        }
        this.cardSelectionActor = cardSelectionActor

        initCards(onjScreen)
        addCards(onjScreen)
    }

    private fun initCards(onjScreen: OnjScreen) {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), onjScreen) { }
    }

    private fun addCards(onjScreen: OnjScreen) {
        val cards = mutableListOf<CardPrototype>()
        for ((name, amount) in SaveState.cardsToDraw) {
            val card = cardPrototypes
                .firstOrNull { it.name == name }
                ?: throw RuntimeException("unknown card in savefile: $name")
            repeat(amount) { cards.add(card) }
        }

        if (cards.isEmpty()) {
            displayCardsEmptyActor()
            return
        }

        cards.shuffle()

        repeat(min(cardsToSelect, cards.size)) {
            val cardProto = cards.first()
            cards.removeFirst()
            val card = cardProto.create()
            this.cards.add(card)

            card.actor.setScale(cardScale)
            card.actor.reportDimensionsWithScaling = true
            card.actor.ignoreScalingWhenDrawing = true
            card.actor.invalidate()
            card.actor.onClick { handleClick(card) }

            val cardBehaviour = BehaviourFactory.behaviorOrError(
                cardBehaviourOnj.name,
                cardBehaviourOnj,
                card.actor
            )
            cardBehaviour.bindCallbacks(onjScreen)

            val cardSelectionActor = cardSelectionActor
            if (cardSelectionActor is FlexBox) {
                cardSelectionActor.add(card.actor)
            } else {
                cardSelectionActor.addActor(card.actor)
            }
            onjScreen.addActorToRoot(card.actor.hoverDetailActor)
            card.actor.hoverDetailActor.isVisible = false
        }
        cardSelectionActor.invalidateHierarchy()
    }

    private fun displayCardsEmptyActor() {
        val label = CustomLabel(
            emptyText,
            Label.LabelStyle(emptyFont, emptyFontColor)
        )
        label.setFontScale(emptyFontScale)
        cardSelectionActor.addActor(label)
    }

    override fun update() {
//        onjScreen.invalidateEverything()
        var isCardHoveredOver = false
        for (card in cards) if (card.actor.isHoveredOver) {
            isCardHoveredOver = true
            if (currentHoverDetail === card.actor.hoverDetailActor) break
            currentHoverDetail?.isVisible = false
            currentHoverDetail?.let { onjScreen.removeActorFromRoot(it) }
            currentHoverDetail = card.actor.hoverDetailActor
            onjScreen.addActorToRoot(currentHoverDetail!!)
            currentHoverDetail!!.isVisible = true
            layoutHoverDetail(currentHoverDetail!!, card)
            break
        }
        if (!isCardHoveredOver && currentHoverDetail != null) {
            currentHoverDetail!!.isVisible = false
            onjScreen.removeActorFromRoot(currentHoverDetail!!)
            currentHoverDetail = null
        }
        if (currentHoverDetail != null) layoutHoverDetail(currentHoverDetail!!, currentHoverDetail!!.card)

//        for (card in cards) {
//            val cardActor = card.actor
//            val hoverDetail = cardActor.hoverDetailActor
//            val cardSize = cardActor.width
//            hoverDetail.forcedWidth = cardSize * 1.4f
//            val (x, y) = cardActor.localToStageCoordinates(Vector2(0f, 0f))
//            hoverDetail.setBounds(
//                x + cardSize / 2 - hoverDetail.forcedWidth / 2,
//                y - hoverDetail.prefHeight,
//                hoverDetail.forcedWidth,
//                hoverDetail.prefHeight
//            )
//        }
    }

    private fun layoutHoverDetail(hoverDetailActor: CardDetailActor, card: Card) {
        val (x, y) = card.actor.localToStageCoordinates(Vector2(0f,0f))
        val cardSize = card.actor.width //* card.actor.scaleX
        hoverDetailActor.forcedWidth = cardSize * 2
        hoverDetailActor.width = hoverDetailActor.forcedWidth
        hoverDetailActor.layout()
        hoverDetailActor.setBounds(
            (x + cardSize / 2 - hoverDetailActor.forcedWidth / 2)
                .between(0f, onjScreen.viewport.worldWidth - hoverDetailActor.forcedWidth),
            y - hoverDetailActor.prefHeight,
            hoverDetailActor.forcedWidth,
            hoverDetailActor.prefHeight
        )
//        hoverDetailActor.invalidateHierarchy()
    }

    override fun end() {
        currentHoverDetail?.let { onjScreen.removeActorFromRoot(it) }
    }

    private fun handleClick(card: Card) {
        FourtyFiveLogger.debug(logTag, "chose card $card")
        SaveState.drawCard(card)
        SaveState.write()
        FourtyFive.changeToScreen(ScreenBuilder(Gdx.files.internal(nextScreen)).build())
    }

    companion object {

        const val logTag = "CardSelection"

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.CardPrototype
import com.fourinachamber.fourtyfive.game.SaveState
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.min
import kotlin.properties.Delegates

/**
 * controls a screen that allows selecting cards
 */
class CardSelectionScreenController(private val onj: OnjNamedObject) : ScreenController() {

    private val cardSelectionActorName = onj.get<String>("cardSelectionActorName")
    private val nextScreen = onj.get<String>("nextScreen")
    private val cardConfigFile = onj.get<String>("cardConfigFile")
    private val cardAtlasFile = onj.get<String>("cardAtlasFile")
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

    private lateinit var detailFont: BitmapFont
    private lateinit var detailFontColor: Color
    private lateinit var detailBackground: Drawable
    private var detailFontScale by Delegates.notNull<Float>()
    private lateinit var detailOffset: Vector2
    private var detailWidth by Delegates.notNull<Float>()

    private lateinit var hoverDetailActor: CustomLabel

    override fun init(onjScreen: OnjScreen) {
        this.onjScreen = onjScreen

        detailFont = onjScreen.fontOrError(onj.get<String>("detailFont"))
        detailFontColor = onj.get<Color>("detailFontColor")
        detailBackground = TextureRegionDrawable(onjScreen.textureOrError(onj.get<String>("detailBackgroundTexture")))
        detailFontScale = onj.get<Double>("detailFontScale").toFloat()
        detailOffset = Vector2(onj.get<Double>("detailOffsetX").toFloat(), onj.get<Double>("detailOffsetY").toFloat())
        detailWidth = onj.get<Double>("detailWidth").toFloat()
        emptyFont = onjScreen.fontOrError(onj.get<String>("emptyFont"))

        hoverDetailActor = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
        hoverDetailActor.isVisible = false
        hoverDetailActor.fixedZIndex = Int.MAX_VALUE
        hoverDetailActor.wrap = true
        hoverDetailActor.width = detailWidth
        hoverDetailActor.background = detailBackground
        onjScreen.addActorToRoot(hoverDetailActor)

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

        val cardAtlas = TextureAtlas(Gdx.files.internal(cardAtlasFile))

        for (region in cardAtlas.regions) {
            onjScreen.addTexture("${Card.cardTexturePrefix}${region.name}", region)
        }

        onjScreen.addDisposable(cardAtlas)

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

            card.actor.onClick { handleClick(card) }

            val cardBehaviour = BehaviourFactory.behaviorOrError(
                cardBehaviourOnj.name,
                cardBehaviourOnj,
                card.actor
            )
            cardBehaviour.bindCallbacks(onjScreen)

            cardSelectionActor.addActor(card.actor)
        }
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
        for (card in cards) if (card.actor.isHoveredOver) {
            hoverDetailActor.isVisible = true
            hoverDetailActor.setText(card.description)
            val (x, y) = card.actor.localToStageCoordinates(Vector2(0f, 0f))
            hoverDetailActor.height = hoverDetailActor.prefHeight
            hoverDetailActor.width = detailWidth

            val toLeft = x + card.actor.width + detailWidth > onjScreen.stage.viewport.worldWidth

            hoverDetailActor.setPosition(
                if (toLeft) x - detailWidth else x + card.actor.width + detailOffset.x,
                y + card.actor.height / 2 - hoverDetailActor.height / 2 + detailOffset.y
            )
            return
        }
        hoverDetailActor.isVisible = false
    }

    override fun end() {
        onjScreen.removeActorFromRoot(hoverDetailActor)
    }

    private fun handleClick(card: Card) {
        FourtyFiveLogger.debug(logTag, "chose card $card")
        SaveState.drawCard(card)
        SaveState.write()
        FourtyFive.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(nextScreen)).build()
    }

    companion object {

        const val logTag = "CardSelection"

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

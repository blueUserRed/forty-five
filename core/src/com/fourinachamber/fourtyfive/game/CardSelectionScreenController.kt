package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
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
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import ktx.actors.onClick
import onj.*
import java.lang.Integer.min
import kotlin.properties.Delegates

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
    private lateinit var screenDataProvider: ScreenDataProvider

    private lateinit var detailFont: BitmapFont
    private lateinit var detailFontColor: Color
    private lateinit var detailBackground: Drawable
    private var detailFontScale by Delegates.notNull<Float>()
    private lateinit var detailOffset: Vector2
    private var detailWidth by Delegates.notNull<Float>()

    private lateinit var hoverDetailActor: CustomLabel

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider

        detailFont = screenDataProvider.fonts[onj.get<String>("detailFont")]
            ?: throw RuntimeException("unknown font: ${onj.get<String>("detailFont")}")
        detailFontColor = Color.valueOf(onj.get<String>("detailFontColor"))
        detailBackground = TextureRegionDrawable(
            screenDataProvider.textures[onj.get<String>("detailBackgroundTexture")]
                ?: throw RuntimeException("unknown texture: ${onj.get<String>("detailBackgroundTexture")}")
        )
        detailFontScale = onj.get<Double>("detailFontScale").toFloat()
        detailOffset = Vector2(onj.get<Double>("detailOffsetX").toFloat(), onj.get<Double>("detailOffsetY").toFloat())
        detailWidth = onj.get<Double>("detailWidth").toFloat()

        hoverDetailActor = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
        hoverDetailActor.isVisible = false
        hoverDetailActor.fixedZIndex = Int.MAX_VALUE
        hoverDetailActor.wrap = true
        hoverDetailActor.width = detailWidth
        hoverDetailActor.background = detailBackground
//        hoverDetailActor.setPosition(detailOffset.x, detailOffset.y)
        screenDataProvider.addActorToRoot(hoverDetailActor)

        val cardSelectionActor = screenDataProvider.namedActors[cardSelectionActorName]
            ?: throw RuntimeException("no actor with name $cardSelectionActorName")
        if (cardSelectionActor !is WidgetGroup) {
            throw RuntimeException("actor with name $cardSelectionActorName must be a WidgetGroup!")
        }
        this.cardSelectionActor = cardSelectionActor

        initCards(screenDataProvider)
        addCards(screenDataProvider)

    }

    private fun initCards(screenDataProvider: ScreenDataProvider) {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cardAtlas = TextureAtlas(Gdx.files.internal(cardAtlasFile))

        for (region in cardAtlas.regions) {
            screenDataProvider.addTexture("${Card.cardTexturePrefix}${region.name}", region)
        }

        screenDataProvider.addDisposable(cardAtlas)

        cardPrototypes = Card.getFrom(onj.get<OnjArray>("cards"), screenDataProvider.textures) { }
    }

    private fun addCards(screenDataProvider: ScreenDataProvider) {
        val cards = mutableListOf<CardPrototype>()
        for ((name, amount) in SaveState.cardsToDraw) {
            val card = cardPrototypes
                .firstOrNull { it.name == name }
                ?: throw RuntimeException("unknown card in savefile: $name")
            repeat(amount) { cards.add(card) }
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
            cardBehaviour.bindCallbacks(screenDataProvider)

            cardSelectionActor.addActor(card.actor)
        }
    }

    override fun update() {
        for (card in cards) if (card.actor.isHoveredOver) {
            hoverDetailActor.isVisible = true
            hoverDetailActor.setText(card.description)
            val (x, y) = card.actor.localToStageCoordinates(Vector2(0f, 0f))
            hoverDetailActor.height = hoverDetailActor.prefHeight
            hoverDetailActor.setPosition(
                x + detailOffset.x + card.actor.width / 2 - detailWidth / 2,
                y + detailOffset.y - hoverDetailActor.height
            )
            return
        }
        hoverDetailActor.isVisible = false
    }

    override fun end() {
        screenDataProvider.removeActorFromRoot(hoverDetailActor)
    }

    private fun handleClick(card: Card) {
        SaveState.drawCard(card)
        SaveState.write()
        FourtyFive.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(nextScreen)).build()
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.ShopMapEvent
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.math.ceil
import kotlin.random.Random

class CardCollectionScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private val cardsFile: String = onj.get<String>("cardsFile")
    //    private val lockedCardTextureName: String,  //TODO locked cards

    //TODO ugly, could be "val"
    private lateinit var cardPrototypes: List<CardPrototype>
    private lateinit var _allCards: MutableList<Card>

    private val lockedTextureName: String = "" //TODO this

    private val moreInfoTextureName: String = "" //TODO this


    private val totalPagesGlobalStringName: String = "overlay.cardCollection.totalPages"
    private val curPageGlobalStringName: String = "overlay.cardCollection.curPage"

    private var curPage = 0

    @Inject(name = "card_collection_widget")
    private lateinit var cardCollectionWidget: CustomFlexBox


//    @Inject(name = "card_collection_cards_parent") //TODO name
    private lateinit var cardsParentWidget: CustomFlexBox

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        val cardsOnj = OnjParser.parseFile(cardsFile)
        cardsFileSchema.assertMatches(cardsOnj)
        cardsOnj as OnjObject
        cardPrototypes = (Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) }))
            .filter { "not in collection" !in it.tags }
        _allCards = cardPrototypes
            .filter { it.name in SaveState.cards } //TODO maybe this filter should be removed, as it might be the case that cards are bought on the screen and the they should be visible in the colleciton
            .map { it.create(screen, true) }
            .toMutableList()

        fun format(a: Number): String = a.toInt().toString().padStart(2, '0')
        TemplateString.updateGlobalParam(totalPagesGlobalStringName, format(ceil(cardPrototypes.size / 15.0)))
        TemplateString.updateGlobalParam(curPageGlobalStringName, format(curPage + 1))

        cardCollectionWidget.onDisplay = { getInOutTimeLine(isGoingIn = true, cardCollectionWidget) }
        cardCollectionWidget.onHide = { getInOutTimeLine(isGoingIn = false, cardCollectionWidget) }
    }

    private fun getInOutTimeLine(isGoingIn: Boolean, target: CustomFlexBox) = Timeline.timeline {
        val amount = screen.stage.viewport.worldHeight
        action {
            if (isGoingIn) {
                cardCollectionWidget.isVisible = true
                target.offsetY = -amount
            }
        }
        val action = CustomMoveByAction(
            target,
            (if (isGoingIn) Interpolation.exp10Out else Interpolation.exp5In),
            relY = amount * (if (isGoingIn) 1 else -1),
            duration = 200F
        )
        action { target.addAction(action) }
        delayUntil { action.isComplete }
        action {
            if (!isGoingIn) cardCollectionWidget.isVisible = false
            else {
                cardCollectionWidget.invalidate()
            }
        }
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }
        const val LOG_TAG: String = "CardCollection"
    }
}

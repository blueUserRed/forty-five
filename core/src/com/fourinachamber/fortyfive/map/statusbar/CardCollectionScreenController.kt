package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.game.card.DetailDescriptionHandler
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
import kotlin.math.min
import kotlin.random.Random

class CardCollectionScreenController(onj: OnjObject) : ScreenController() {

    private lateinit var screen: OnjScreen
    private val cardsFile: String = onj.get<String>("cardsFile")
    //    private val lockedCardTextureName: String,  //TODO locked cards

    //TODO ugly, could be "val"
    private lateinit var cardPrototypes: List<CardPrototype>
    private lateinit var _allCards: List<Card>

    private val totalPagesGlobalStringName: String = "overlay.cardCollection.totalPages"
    private val curPageGlobalStringName: String = "overlay.cardCollection.curPage"

    private var curPage = 0

    @Inject(name = "card_collection_widget")
    private lateinit var cardCollectionWidget: CustomFlexBox


    @Inject(name = "card_collection_cards_parent")
    private lateinit var cardsParentWidget: CustomFlexBox

    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        val cardsOnj = OnjParser.parseFile(cardsFile)
        cardsFileSchema.assertMatches(cardsOnj)
        cardsOnj as OnjObject
        cardPrototypes = (Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) }))
            .filter { "not in collection" !in it.tags }
        _allCards = cardPrototypes
            .map { it.create(screen, true) }
            .toMutableList()
//        _allCards.forEach { it.actor.actorTemplate = "card_hover_detail_glow" } //TODO comment back in
        fun format(a: Number): String = a.toInt().toString().padStart(2, '0')
        TemplateString.updateGlobalParam(totalPagesGlobalStringName, format(ceil(cardPrototypes.size / 15.0)))
        TemplateString.updateGlobalParam(curPageGlobalStringName, format(curPage + 1))

        cardCollectionWidget.onDisplay = { getInOutTimeLine(isGoingIn = true, cardCollectionWidget) }
        cardCollectionWidget.onHide = { getInOutTimeLine(isGoingIn = false, cardCollectionWidget) }
        loadCollection()
    }


    private fun reloadCollection() {
        cardsParentWidget.children.filterIsInstance<Group>()
            .forEach { it.children.forEach { it2 -> screen.removeActorFromScreen(it2) } }
        loadCollection()
    }

    private fun loadCollection() {
        val parents = cardsParentWidget.children.filterIsInstance<CustomFlexBox>()
        for (i in (0 until (min(parents.size, _allCards.size - curPage * parents.size)))) {
            val c = _allCards[i + curPage * parents.size]
            if (c.name in SaveState.cards)
                screen.screenBuilder.addDataToWidgetFromTemplate(
                    "card_collection_slot_card",
                    mapOf(),
                    parents[i],
                    screen,
                    c.actor
                )
            else if (!c.lockedDescription.isNullOrEmpty()) { //TODO this if condition when locked system exists
                screen.screenBuilder.generateFromTemplate(
                    "card_collection_slot_glow_background",
                    mapOf(),
                    parents[i],
                    screen
                )
                val curActor = screen.screenBuilder.generateFromTemplate(
                    "card_collection_slot",
                    mapOf(
                        "background" to "collection_slot_locked",
                        "hoverText" to c.lockedDescription
                    ),
                    parents[i],
                    screen
                ) as CustomFlexBox
                curActor.touchable = Touchable.enabled
                curActor.additionalHoverData["effects"] = DetailDescriptionHandler.allTextEffects
                println(DetailDescriptionHandler.allTextEffects)
            } else {
                screen.screenBuilder.generateFromTemplate(
                    "card_collection_slot",
                    mapOf("background" to "collection_slot_missing"),
                    parents[i],
                    screen
                )
            }
        }
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

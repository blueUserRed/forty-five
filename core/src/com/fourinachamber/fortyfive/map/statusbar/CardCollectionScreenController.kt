package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.game.card.DetailDescriptionHandler
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.math.ceil
import kotlin.math.min

class CardCollectionScreenController(private val screen: OnjScreen, onj: OnjObject) : ScreenController() {

    //    private val lockedCardTextureName: String,  //TODO locked cards

    //TODO ugly, could be "val"
    private lateinit var cardPrototypes: List<CardPrototype>
    private lateinit var _allCards: List<Card>

    private val totalPagesGlobalStringName: String = "overlay.cardCollection.totalPages"
    private val curPageGlobalStringName: String = "overlay.cardCollection.curPage"

    private var curPage = 0
    private var nbrOfPages = -1

    @Inject(name = "card_collection_widget")
    private lateinit var cardCollectionWidget: CustomFlexBox


    @Inject(name = "card_collection_cards_parent")
    private lateinit var cardsParentWidget: CustomFlexBox

    override fun init(context: Any?) {
        val cardsOnj = ConfigFileManager.getConfigFile("cards")
        cardPrototypes = (Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) }))
            .filter { "not in collection" !in it.tags }
        cardPrototypes =
            sort((Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) }))
                .filter { "not in collection" !in it.tags }.toMutableList()
            )
        _allCards = cardPrototypes
            .map { it.create(screen, true) }
            .toMutableList()
        //        _allCards.forEach { it.actor.actorTemplate = "card_hover_detail_glow" } //TODO comment back in once it exists
        nbrOfPages = ceil(cardPrototypes.size / 15.0).toInt()
        TemplateString.updateGlobalParam(totalPagesGlobalStringName, formatToTwoDigits(nbrOfPages))

        cardCollectionWidget.onDisplay = { getInOutTimeLine(isGoingIn = true, cardCollectionWidget) }
        cardCollectionWidget.onHide = { getInOutTimeLine(isGoingIn = false, cardCollectionWidget) }
        loadCollection()
    }

    private fun sort(original: MutableList<CardPrototype>): List<CardPrototype> {
        val res: MutableList<CardPrototype> = mutableListOf()
        var i = 0
        var notFoundCounter = 0 //TODO ugly
        while (original.isNotEmpty()) {
            val curRes = original.filter { "pool$i" in it.tags }
            original.removeAll(curRes)
            if (curRes.isEmpty())notFoundCounter++
            if (notFoundCounter==3) break
            res.addAll(curRes)
            i++
        }
        res.addAll(original)
        return res
    }

    private fun formatToTwoDigits(a: Number): String = a.toInt().toString().padStart(2, '0')

    private var lastEvent: ButtonClickEvent? = null

    @EventHandler
    fun nextPage(event: ButtonClickEvent, actor: Actor) {
        if (lastEvent == event) return
        if ((++curPage) == nbrOfPages) curPage = 0
        reloadCollection()
        lastEvent =
            event //TODO VERY UGLY, marvin idk why this happens, but it needs to be fixed (this method is always called twice for one buttonpress, idk why)
    }

    @EventHandler
    fun prevPage(event: ButtonClickEvent, actor: Actor) {
        if (lastEvent == event) return
        if ((--curPage) < 0) curPage += nbrOfPages
        reloadCollection()
        lastEvent = event
    }

    private fun reloadCollection() {
        cardsParentWidget.children.filterIsInstance<Group>().forEach {
            while (it.children.size > 0) screen.removeActorFromScreen(it.children[0])
        }
        loadCollection()
    }

    private fun loadCollection() {
        TemplateString.updateGlobalParam(curPageGlobalStringName, formatToTwoDigits(curPage + 1))


        val parents = cardsParentWidget.children.filterIsInstance<CustomFlexBox>()
        for (i in (0 until (min(parents.size, _allCards.size - curPage * parents.size)))) {
            val c = _allCards[i + curPage * parents.size]
            if (c.name in PermaSaveState.collection)
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
                screen.enterState("showHoverDetailGlow")
                cardCollectionWidget.isVisible = true
                target.offsetY = -amount
            } else {
                screen.leaveState("showHoverDetailGlow")
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

}

package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.ColorAction
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BounceOutAction
import com.fourinachamber.fortyfive.utils.*

class DraftScreenController : ScreenController() {

    @Inject
    private lateinit var card1: CustomFlexBox

    @Inject
    private lateinit var card2: CustomFlexBox

    @Inject
    private lateinit var card3: CustomFlexBox

    private lateinit var screen: OnjScreen

    private var inDiscardAnim: Boolean = false

    private val timeline: Timeline = Timeline()

    private val targetAmount: Int = SaveState.Deck.minDeckSize
    private var currentAmount: Int by templateParam(
        "draft.current", 0
    )

    private val chosenCards: MutableList<String> = mutableListOf()

    private lateinit var cards: Array<CustomFlexBox>

    private lateinit var context: GameController.EncounterContext

    override fun init(onjScreen: OnjScreen, context: Any?) {
        this.context = context as? GameController.EncounterContext
            ?: throw RuntimeException("DraftScreenController needs a context of type EncounterContext")
        this.screen = onjScreen
        cards = arrayOf(card1, card2, card3)
        TemplateString.updateGlobalParam("draft.target", targetAmount)
        timeline.startTimeline()
        timeline.appendAction(Timeline.timeline {
            action {
                newCards()
            }
        }.asAction())
    }

    override fun update() {
        timeline.updateTimeline()
    }

    @EventHandler
    fun cardChosen(event: ButtonClickEvent, actor: CustomFlexBox) {
        if (inDiscardAnim) return
        inDiscardAnim = true
        val cardActor = actor
            .children
            .find { it is CardActor }
        cardActor as CardActor
        chosenCards.add(cardActor.card.name)
        val animateOutTimeline = cards
            .filter { it !== actor }
            .map { getDiscardAction(it) }
            .collectParallelTimeline()
        val timeline = Timeline.timeline {
            include(animateOutTimeline)
            action {
                currentAmount++
                if (currentAmount >= targetAmount) {
                    finished()
                } else {
                    inDiscardAnim = false
                    newCards()
                }
            }
        }
        this.timeline.appendAction(timeline.asAction())
    }

    private fun newCards() {
        val allCards = RandomCardSelection.allAvailableCardPrototypes
        var i = 0
        val usedCards = mutableListOf<String>()
        while (i < cards.size) {
            val cardProto = allCards.random()
            if (cardProto.name in usedCards) continue
            usedCards.add(cardProto.name)
            val card = cardProto.create(screen, areHoverDetailsEnabled = true)
            val previous = cards[i].children.find { it is CardActor }
            previous as CardActor?
            previous?.card?.dispose()
            screen.removeAllStyleManagersOfChildren(cards[i])
            cards[i].clear()
            cards[i].add(card.actor)
                .setWidthPercent(100f)
                .setHeightPercent(100f)
            i++
        }
    }

    private fun finished() {
        val context = object : GameController.EncounterContext {

            override val encounterIndex: Int = context.encounterIndex
            override val forwardToScreen: String = context.forwardToScreen

            override val forceCards: List<String> = chosenCards

            override fun completed() = context.completed()
        }
        MapManager.changeToEncounterScreen(context, immediate = true)
    }

    private fun getDiscardAction(actor: CustomFlexBox): Timeline {
        val bounceOutAction = BounceOutAction(
            Vector2((-1_000f..1_000f).random(), (1_500f..2_000f).random()),
            (-200f..200f).random(),
            Vector2(0f, -4_500f),
            0f,
            500
        )
        val alphaAction = ColorAction()
        alphaAction.endColor = Color(1f, 1f, 1f, 0f)
        alphaAction.duration = 0.5f
        return Timeline.timeline {
            action {
                actor.addAction(bounceOutAction)
                actor.addAction(alphaAction)
            }
            delayUntil { bounceOutAction.isComplete && alphaAction.isComplete }
            action {
                actor.removeAction(bounceOutAction)
                actor.removeAction(alphaAction)
                actor.color.a = 1f
            }
        }
    }

}

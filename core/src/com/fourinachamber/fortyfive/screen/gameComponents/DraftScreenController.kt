package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.ColorAction
import com.fourinachamber.fortyfive.map.events.RandomCardSelection
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BounceOutAction
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.collectParallelTimeline
import com.fourinachamber.fortyfive.utils.random

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

    private lateinit var cards: Array<CustomFlexBox>
    private val cardNames: Array<String> = arrayOf("", "", "")

    override fun init(onjScreen: OnjScreen, context: Any?) {
        this.screen = onjScreen
        cards = arrayOf(card1, card2, card3)
        timeline.startTimeline()
    }

    override fun update() {
        timeline.updateTimeline()
    }

    @EventHandler
    fun cardChosen(event: ButtonClickEvent, actor: CustomFlexBox) {
        if (inDiscardAnim) return
        discardCards(keep = actor)
    }

    private fun newCards() {
        val allCards = RandomCardSelection.allCardPrototypes
        repeat(3) {
            val cardProto = allCards.random()
            val card = cardProto.create(screen, areHoverDetailsEnabled = true)
        }
    }

    private fun discardCards(keep: CustomFlexBox) {
        inDiscardAnim = true
        val animateOutTimeline = cards
            .filter { it !== keep }
            .map { getDiscardAction(it) }
            .collectParallelTimeline()
        val timeline = Timeline.timeline {
            action {
            }
            include(animateOutTimeline)
            action {
                inDiscardAnim = false
                newCards()
            }
        }
        this.timeline.appendAction(timeline.asAction())
    }

    private fun getDiscardAction(actor: CustomFlexBox): Timeline {
        val bounceOutAction = BounceOutAction(
            Vector2((-1_000f..1_000f).random(), (1_500f..2_000f).random()),
            (-100f..100f).random(),
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
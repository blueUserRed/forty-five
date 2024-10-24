package com.fourinachamber.fortyfive.game.controller

import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.EncounterModifier
import com.fourinachamber.fortyfive.game.GameAnimation
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.StatusEffect
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.game.card.Trigger
import com.fourinachamber.fortyfive.game.card.TriggerInformation
import com.fourinachamber.fortyfive.game.controller.OldGameController.Companion.logTag
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.NewCardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.general.Inject
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import kotlin.math.floor

class NewGameController(
    override val screen: OnjScreen,
    val gameEvents: EventPipeline
) : ScreenController(), GameController {

    private val gameDirector = GameDirector(this)

    override val gameRenderPipeline: GameRenderPipeline = GameRenderPipeline(screen)

    override val playerLost: Boolean = false

    override var curReserves: Int = Config.baseReserves

    override val isUIFrozen: Boolean
        get() = TODO("Not yet implemented")

    override val revolverRotationCounter: Int
        get() = TODO("Not yet implemented")

    override val turnCounter: Int = 0

    override val playerStatusEffects: List<StatusEffect>
        get() = TODO("Not yet implemented")

    override val isEverlastingDisabled: Boolean
        get() = TODO("Not yet implemented")

    override val cardsInHand: List<Card>
        get() = cardHand.allCards()

    private val _encounterModifiers: MutableList<EncounterModifier> = mutableListOf()
    override val encounterModifiers: List<EncounterModifier>
        get() = _encounterModifiers

    override val curPlayerLives: Int
        get() = TODO("Not yet implemented")

    override val activeEnemies: List<Enemy>
        get() = TODO("Not yet implemented")

    override val allEnemies: List<Enemy>
        get() = TODO("Not yet implemented")

    @Inject(name = "shoot_button")
    override lateinit var shootButton: Actor

    @Inject override lateinit var revolver: Revolver

    @Inject private lateinit var cardHand: NewCardHand

    override lateinit var encounterContext: EncounterContext
        private set

    private var cardPrototypes: List<CardPrototype> = listOf()

    private val _cardStack: MutableList<Card> = mutableListOf()
    override val cardStack: List<Card>
        get() = _cardStack

    private val createdCards: MutableList<Card> = mutableListOf()

    private lateinit var defaultBullet: CardPrototype

    private val mainTimeline: Timeline = Timeline().also { it.startTimeline() }
    private val animTimelines: MutableList<Timeline> = mutableListOf()

    private val tutorialText: MutableList<GameDirector.GameTutorialTextPart> = mutableListOf()

    var cardsDrawn: Int = 0
        private set

    override fun init(context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterContext = context
        FortyFive.currentGame = this

        gameDirector.init()

        gameEvents.watchFor<NewCardHand.CardDraggedOntoSlotEvent> { loadBulletFromHandInRevolver(it.card, it.slot.num) }
        gameEvents.watchFor<Events.Shoot> { shoot() }
        bindCardEffects()

        initCards()
        updateReserves(Config.baseReserves)
        appendMainTimeline(Timeline.timeline {
            delay(300)
            include(drawCardsTimeline(Config.cardsToDrawInFirstRound))
        })
    }

    override fun onShow() {
        FortyFive.useRenderPipeline(gameRenderPipeline)
    }

    private fun bindCardEffects() {
        gameEvents.watchFor<Events.CardChangedZoneEvent> { event ->
            event.card.changeZone(event.newZone, this)
            val trigger = Trigger.ZoneChange(event.oldZone, event.newZone, false)
            event.append {
                include(checkTrigger(trigger, event.triggerInformation))
            }
        }
    }

    private fun checkTrigger(trigger: Trigger, triggerInformation: TriggerInformation): Timeline = createdCards
        .map { it.checkEffects(trigger, triggerInformation, this) }
        .collectTimeline()

    private fun updateReserves(newReserves: Int, sourceActor: Actor? = null) {
        if (curReserves == newReserves) return
        val prevReserves = curReserves
        curReserves = newReserves
        gameEvents.fire(Events.ReservesChanged(prevReserves, newReserves, sourceActor, this))
    }

    override fun update() {
        TemplateString.updateGlobalParam("game.cardsInStack", _cardStack.size)
        animTimelines.forEach(Timeline::updateTimeline)
        mainTimeline.updateTimeline()
    }

    private fun initCards() {
        val onj = ConfigFileManager.getConfigFile("cards")

        val cards = gameDirector.encounter.forceCards
            ?: encounterContext.forceCards
            ?: SaveState.curDeck.cards

        val cardsArray = onj.get<OnjArray>("cards")

        cardPrototypes = Card
            .getFrom(cardsArray) { card ->
                createdCards.add(card)
                _encounterModifiers.forEach { it.initBullet(card) }
            }
            .toMutableList()

        cards.forEach { cardName ->
            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            _cardStack.add(card.create(this.screen))
        }

        if (gameDirector.encounter.shuffleCards) _cardStack.shuffle()

        FortyFiveLogger.debug(logTag, "card stack: $_cardStack")

        val defaultBulletName = onj.get<String>("defaultBullet")

        defaultBullet = cardPrototypes
            .firstOrNull { it.name == defaultBulletName }
            ?: throw RuntimeException("unknown default bullet: $defaultBulletName")
    }

    override fun cardSelectionPopupTimeline(
        text: String,
        exclude: Card?
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun destroyCardTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    override fun tryToPutCardsInHandTimeline(
        cardName: String,
        amount: Int
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun bounceBulletTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    override fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun drawCardsTimeline(
        amount: Int,
        isSpecial: Boolean,
        fromBottom: Boolean,
        sourceCard: Card?,
    ): Timeline = Timeline.timeline { later {

        var cardsToDraw = amount
        cardsToDraw += encounterModifiers.sumOf {
            if (isSpecial) it.additionalCardsToDrawInSpecialDraw() else it.additionalCardsToDrawInNormalDraw()
        }
        cardsToDraw = floor(
            encounterModifiers
                .fold(cardsToDraw.toFloat()) { acc, cur ->
                    acc * (if (isSpecial) cur.cardsInSpecialDrawMultiplier() else cur.cardsInNormalDrawMultiplier())
                }
        ).toInt()
        cardsToDraw = maxSpaceInHand(cardsToDraw)

        repeat(cardsToDraw) {
            include(drawCardTimeline(fromBottom, sourceCard))
        }

        skipping { skip ->
            action { if (cardsToDraw <= 0) skip() }
            later {
                val info = TriggerInformation(
                    controller = this@NewGameController,
                    amountOfCardsDrawn = cardsToDraw,
                    multiplier = cardsToDraw,
                    sourceCard = sourceCard,
                )
                val event = Events.CardsDrawnTimeline(cardsToDraw, isSpecial, fromBottom, info)
                gameEvents.fire(event)
                include(event.createTimeline())
            }
        }

    } }

    private fun drawCardTimeline(fromBottom: Boolean, sourceCard: Card?): Timeline = Timeline.timeline {
        var card: Card? = null
        action {
            card = when {
                _cardStack.isEmpty() -> defaultBullet.create(screen)
                fromBottom -> _cardStack.removeLast()
                else -> _cardStack.removeFirst()
            }
            cardHand.addCard(card!!)
            cardsDrawn++
            val event = Events.PlayCardOrbAnimation(card!!.actor)
            gameEvents.fire(event)
            dispatchAnimTimeline(event.orbAnimationTimeline!!)
        }
        includeLater({
            val info = TriggerInformation(controller = this@NewGameController, sourceCard = sourceCard)
            val event = Events.CardChangedZoneEvent(card!!, Zone.DECK, Zone.HAND, info)
            gameEvents.fire(event)
            event.createTimeline()
        })
    }

    private fun maxSpaceInHand(desiredSpace: Int = Int.MAX_VALUE): Int =
        (Config.hardMaxCards - cardHand.amountOfCards).between(0, desiredSpace)

    override fun tryApplyStatusEffectToEnemyTimeline(
        statusEffect: StatusEffect,
        enemy: Enemy
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun damagePlayerTimeline(
        damage: Int,
        triggeredByStatusEffect: Boolean,
        isPiercing: Boolean
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun playerDeathTimeline(): Timeline {
        TODO("Not yet implemented")
    }

    override fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect): Timeline {
        TODO("Not yet implemented")
    }

    override fun putCardFromStackInHandTimeline(
        card: Card,
        source: Card?
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun destroyCardInHandTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    override fun enemyAttackTimeline(
        damage: Int,
        isPiercing: Boolean
    ): Timeline {
        TODO("Not yet implemented")
    }

    override fun putBulletFromRevolverUnderTheDeckTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    override fun shoot() {
        TODO("Not yet implemented")
    }

    override fun gainReserves(amount: Int, source: Actor?) {
        TODO("Not yet implemented")
    }

    override fun tryPay(cost: Int, animTarget: Actor?): Boolean {
        if (cost > curReserves) return false
        SaveState.usedReserves += cost
        FortyFiveLogger.debug(logTag, "$cost reserves were spent, curReserves = $curReserves")
        updateReserves(curReserves - cost, sourceActor = animTarget)
        return true
    }

    override fun addTemporaryEncounterModifier(
        modifier: EncounterModifier,
        validityChecker: (GameController) -> Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun addEncounterModifier(modifier: EncounterModifier) {
        TODO("Not yet implemented")
    }

    override fun addTutorialText(textParts: List<GameDirector.GameTutorialTextPart>) {
        tutorialText.addAll(textParts)
    }

    override fun initEnemyArea(enemies: List<Enemy>) {
    }

    override fun enemyDefeated(enemy: Enemy) {
        TODO("Not yet implemented")
    }

    override fun playGameAnimation(anim: GameAnimation) {
        TODO("Not yet implemented")
    }

    override fun loadBulletFromHandInRevolver(card: Card, slot: Int) {
        var cardInSlot: Card? = null
        val timeline = Timeline.timeline {
            skipping { skip ->
                action {
                    FortyFiveLogger.debug(logTag, "attempting to load bullet $card in revolver slot $slot")
                    cardInSlot = revolver.getCardInSlot(slot)
                    val blockedByCard = cardInSlot != null && !card.canBeReplaced(this@NewGameController, card)
                    val shouldSkip = !card.allowsEnteringGame(this@NewGameController, slot)
                        || blockedByCard
                        || !tryPay(card.baseCost, card.actor)
                    if (!shouldSkip) return@action
                    SoundPlayer.situation("not_allowed", screen)
                    skip()
                }
                action {
                    cardHand.removeCard(card)
                    if (cardInSlot != null) revolver.preAddCard(slot, card)
                }
                includeLater(
                    { cardInSlot!!.replaceTimeline(this@NewGameController, card) },
                    { cardInSlot != null }
                )
                action {
                    revolver.setCard(slot, card)
                }
                val info = TriggerInformation(controller = this@NewGameController, sourceCard = card)
                includeLater({
                    val event = Events.CardChangedZoneEvent(card, Zone.HAND, Zone.REVOLVER, info)
                    gameEvents.fire(event)
                    event.createTimeline()
                })
            }
        }
        appendMainTimeline(timeline)
    }

    override fun appendMainTimeline(timeline: Timeline) {
        mainTimeline.appendAction(timeline.asAction())
    }

    override fun dispatchAnimTimeline(timeline: Timeline) {
        animTimelines.add(timeline)
        timeline.startTimeline()
    }

    override fun cardsInRevolver(): List<Card> = revolver.slots.mapNotNull { it.card }

    override fun cardsInRevolverIndexed(): List<Pair<Int, Card>> = revolver
        .slots
        .filter { it.card != null }
        .map { it.num to it.card!! }

    override fun targetedEnemy(): Enemy {
        TODO("Not yet implemented")
    }

    override fun slotOfCard(card: Card): Int? = revolver.slots.find { it.card === card }?.num

    override fun titleOfCard(cardName: String): String = cardPrototypes.find { it.name == cardName }?.title
        ?: throw RuntimeException("No card with name $cardName")

    override fun end() {
        super.end()
    }

    enum class Zone {
        DECK, HAND, REVOLVER, AFTERLIVE
    }

    object Config {
        const val baseReserves = 4
        const val softMaxCards = 12
        const val hardMaxCards = 20
        const val cardsToDrawInFirstRound = 6
    }

    object Events {
        data class ReservesChanged(
            val old: Int,
            val new: Int,
            val sourceActor: Actor? = null,
            val controller: GameController
        )
        data class PlayCardOrbAnimation(val targetActor: Actor, var orbAnimationTimeline: Timeline? = null)
        data class ParryStateChange(val inParryMenu: Boolean)
        data object Shoot

        abstract class TimelineBuildingEvent {

            val dsl = Timeline.TimelineBuilderDSL()

            inline fun append(block: Timeline.TimelineBuilderDSL.() -> Unit) {
                block(dsl)
            }

            fun createTimeline(): Timeline = dsl.build()
        }

        class CardChangedZoneEvent(
            val card: Card,
            val oldZone: Zone,
            val newZone: Zone,
            val triggerInformation: TriggerInformation,
        ) : TimelineBuildingEvent()

        class CardsDrawnTimeline(
            val amount: Int,
            val isSpecial: Boolean,
            val isFromBottom: Boolean,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

    }
}

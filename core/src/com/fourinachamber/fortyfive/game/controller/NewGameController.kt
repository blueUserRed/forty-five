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
import com.fourinachamber.fortyfive.game.controller.OldGameController.Companion.logTag
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.NewCardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.general.Inject
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Timeline
import onj.value.OnjArray

class NewGameController(
    override val screen: OnjScreen,
    val gameEvents: EventPipeline
) : ScreenController(), GameController {

    private val gameDirector = GameDirector(this)

    override val gameRenderPipeline: GameRenderPipeline
        get() = TODO("Not yet implemented")

    override val playerLost: Boolean
        get() = TODO("Not yet implemented")

    override var curReserves: Int = Config.baseReserves

    override val isUIFrozen: Boolean
        get() = TODO("Not yet implemented")

    override val revolverRotationCounter: Int
        get() = TODO("Not yet implemented")

    override val turnCounter: Int
        get() = TODO("Not yet implemented")

    override val playerStatusEffects: List<StatusEffect>
        get() = TODO("Not yet implemented")

    override val isEverlastingDisabled: Boolean
        get() = TODO("Not yet implemented")

    override val cardsInHand: List<Card>
        get() = TODO("Not yet implemented")

    private val _encounterModifiers: MutableList<EncounterModifier> = mutableListOf()
    override val encounterModifiers: List<EncounterModifier>
        get() = _encounterModifiers

    override val curPlayerLives: Int
        get() = TODO("Not yet implemented")

    override val activeEnemies: List<Enemy>
        get() = TODO("Not yet implemented")

    override val allEnemies: List<Enemy>
        get() = TODO("Not yet implemented")

    override val shootButton: Actor
        get() = TODO("Not yet implemented")

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

    override fun init(context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterContext = context
        FortyFive.currentGame = this

        gameDirector.init()

        gameEvents.watchFor<NewCardHand.CardDraggedOntoSlotEvent> { loadBulletFromHandInRevolver(it.card, it.slot.num) }

        initCards()
        _cardStack.forEach { cardHand.addCard(it) }
        updateReserves(Config.baseReserves)
    }

    private fun updateReserves(newReserves: Int, sourceActor: Actor? = null) {
        val prevReserves = curReserves
        curReserves = newReserves
        gameEvents.fire(Events.ReservesChanged(prevReserves, newReserves, sourceActor, this))
    }

    override fun onShow() {
        FortyFive.useRenderPipeline(GameRenderPipeline(screen))
    }

    override fun update() {
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
        fromBottom: Boolean
    ): Timeline {
        TODO("Not yet implemented")
    }

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
        val timeline = Timeline.timeline {
            skipping { skip ->
                action {
                    FortyFiveLogger.debug(logTag, "attempting to load bullet $card in revolver slot $slot")
                    val cardInSlot = revolver.getCardInSlot(slot)
                    val shouldSkip = !card.allowsEnteringGame(this@NewGameController, slot)
                        || cardInSlot != null
                        || !tryPay(card.baseCost, card.actor)
                    if (!shouldSkip) return@action
                    SoundPlayer.situation("not_allowed", screen)
                    skip()
                }
                action {
                    cardHand.removeCard(card)
                    revolver.setCard(slot, card)
                    println("moved card")
                }
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

    override fun cardsInRevolver(): List<Card> {
        TODO("Not yet implemented")
    }

    override fun cardsInRevolverIndexed(): List<Pair<Int, Card>> {
        TODO("Not yet implemented")
    }

    override fun targetedEnemy(): Enemy {
        TODO("Not yet implemented")
    }

    override fun slotOfCard(card: Card): Int? {
        TODO("Not yet implemented")
    }

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
    }

    object Events {
        data class ReservesChanged(
            val old: Int,
            val new: Int,
            val sourceActor: Actor? = null,
            val controller: GameController
        )
    }
}

package com.microwavestudios.fortyfive.game.controller

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.card.*
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.enemy.EnemyAction
import com.microwavestudios.fortyfive.game.enemy.EnemyActionPrototype
import com.microwavestudios.fortyfive.game.enemy.NextEnemyAction
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.game.widgets.CardHand
import com.microwavestudios.fortyfive.game.widgets.IAfterlife
import com.microwavestudios.fortyfive.game.widgets.ICardHand
import com.microwavestudios.fortyfive.game.widgets.IRevolver
import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.run.RunGeneratorConfig
import com.microwavestudios.fortyfive.screen.Inject
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.commonComponents.IWarningParent
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.EncounterScreen
import com.microwavestudios.fortyfive.screen.screens.LoseRunScreen
import com.microwavestudios.fortyfive.screen.screens.WinRunScreen
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjObject
import kotlin.collections.map
import kotlin.math.floor
import kotlin.random.Random

class GameControllerImpl(
    override val screen: IScreen,
    override val gameEvents: EventPipeline,
    seed: Long,
    private val warningParent: IWarningParent,
    override val afterlife: IAfterlife,
    private val cardPresentationProvider: PresentationProvider
) : ScreenController(), GameController, ResourceBorrower {

    override val random: Random = Random(seed)

    override val playerLost: Boolean = false

    override var curReserves: Int = 0

    override val isUIFrozen: Boolean
        get() = !mainTimeline.isFinished

    override var revolverRotationCounter: Int = 0
        private set

    override var turnCounter: Int = 0
        private set

    private val _playerStatusEffects: MutableList<StatusEffect> = mutableListOf()
    override val playerStatusEffects: List<StatusEffect>
        get() = _playerStatusEffects

    override val isEverlastingDisabled: Boolean
        get() = _encounterBehaviours.any { it.second.disableEverlasting() } ||
                _playerStatusEffects.any { it.disableEverlasting() }

    override val cardsInHand: List<Card>
        get() = cardHand.allCards()

    private val _encounterModifiers: MutableList<EncounterModifier> = mutableListOf()
    override val encounterModifiers: List<EncounterModifier>
        get() = _encounterModifiers

    private val _encounterBehaviours: MutableList<Pair<(GameController) -> Boolean, EncounterBehaviour>> = mutableListOf()
    override val encounterBehaviours: List<EncounterBehaviour>
        get() = _encounterBehaviours.map { it.second }

    override var curPlayerLives: Int
        get() = profile.healthInRun!!
        private set(value) {
            profile.healthInRun = value
        }

    override val activeEnemies: List<Enemy>
        get() = allEnemies.filter { !it.isDefeated }

    override lateinit var allEnemies: List<Enemy>
        private set

    private lateinit var targetedEnemy: Enemy

    @Inject
    override lateinit var revolver: IRevolver
        private set

    @Inject
    override lateinit var cardHand: ICardHand
        private set

    override lateinit var encounterContext: EncounterContext
        private set

    private lateinit var encounter: Encounter

    private var cardPrototypes: List<CardPrototype> = listOf()

    override val cardStack: CardStack = CardStack(mutableListOf())

    private val createdCards: MutableList<Card> = mutableListOf()

    override val allCards: List<Card>
        get() = createdCards

    private lateinit var defaultBullet: CardType

    private val mainTimeline: Timeline = Timeline().also { it.startTimeline() }
    private val animTimelines: MutableList<Timeline> = mutableListOf()

    var cardsDrawn: Int = 0
        private set

    override val hasWon: Boolean
        get() = allEnemies.all { it.isDefeated }

    private val softMaxCardsWarning = warningParent.warning(
        "Maximum Card Number Reached\nAfter this turn, put all but ${Config.softMaxCards} cards at the bottom of your deck.",
        IWarningParent.Level.MID
    )

    private val hardMaxCardsWarning = warningParent.warning(
        "Hard Maximum Card Number Reached\nYou cant draw any more cards this turn. After this turn, put all but ${Config.softMaxCards} cards at the bottom of your deck.",
        IWarningParent.Level.HIGH
    )

    private val enemyDifficulty
        get() = 1f + ((encounter.minorDifficulty - 1f) * RunGeneratorConfig.enemyDamageAdjustment)

    private var inEnemyPhase: Boolean = false

    private lateinit var profile: IProfile

    private val controller: GameController = this
    
    override fun init(context: Any?) {
        require(context is EncounterContext) { "GameScreen needs a context of type encounterMapEvent" }

        encounterContext = context

        FortyFive.logger.debug(logTag, "init GameController, encounter = ${context.encounter}")

        profile = FortyFive.profileManager.currentProfile!!

        FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.BATTLE)

        encounter = encounterContext.encounter
        encounter.encounterModifier.forEach {
            addEncounterModifier(it)
        }
        profile.talismans.forEach {
            addTalisman(it)
        }

        bindGameEventListeners()

        setupEnemies()

        initCards()
        updateReserves(Config.baseReserves)
        appendMainTimeline(Timeline.timeline {
            delay(300)
            updateReserves(currentTurnStartReserves())
            action { chooseEnemyActions() }
            later {
                cardStack.cards().forEach { card ->
                    val info = createTriggerInfo(card)
                    val eventBefore = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.STACK, true, info)
                    val eventAfter = eventBefore.copy(before = false)
                    gameEvents.fire(eventBefore)
                    include(eventBefore.createTimeline())
                    includeLater({
                        gameEvents.fire(eventAfter)
                        eventAfter.createTimeline()
                    })
                }
            }
            includeLater({
                val toDraw = Config.cardsToDrawInFirstRound +
                        _encounterBehaviours.sumOf { it.second.additionalCardsToDrawInInitialDraw() }
                drawCardsTimeline(toDraw)
            })
            later {
                val startTriggerInformation = createTriggerInfo(null)
                val startEvent = Events.TurnBeginEvent(startTriggerInformation)
                gameEvents.fire(startEvent)
                include(startEvent.createTimeline())
            }
        })
    }

    private fun setupEnemies() {
        allEnemies = encounter.createEnemies()
        gameEvents.fire(Events.SetupEnemies(allEnemies))

        var selectedEnemy: Enemy = allEnemies.first()
        gameEvents.fire(Events.EnemySelected(selectedEnemy))

        gameEvents.watchFor<Events.EnemySelected> { (enemy) ->
            selectedEnemy = enemy
        }

        allEnemies.forEach { enemy ->
            enemy.enemyEvents.watchFor<Enemy.EnemyDefeated> {
                if (selectedEnemy != enemy) return@watchFor
                val newEnemy = allEnemies.firstOrNull { !it.isDefeated } ?: allEnemies.first()
                gameEvents.fire(Events.EnemySelected(newEnemy))
            }
        }
    }

    override fun onShow() {
//        FortyFive.useRenderPipeline(gameRenderPipeline)
    }

    private fun bindGameEventListeners() {
        gameEvents.watchFor<Any> { e ->
            if (e !is EncounterScreen.UpdateUiEvent) FortyFive.logger.debug(logTag, "Game Event: $e")
        }
        gameEvents.watchFor<Events.TimelineBuildingEvent> { event ->
            event.append { later {
                allEnemies.forEach { enemy ->
                    val removed = enemy.checkStatusEffectValidity()
                    if (removed.isEmpty()) return@forEach
                    val info = createTriggerInfo(null)
                    removed
                        .map { effect ->
                            val situation = GameSituation.EnemyStatusEffectsChanged(enemy, effect, false)
                            checkTrigger(situation, info)
                        }
                        .collectTimeline()
                        .let { include(it) }
                }
            } }
        }
        gameEvents.watchFor<Events.ParryStateChange> { (inParryMenu) ->
            val renderPipeline = FortyFive.currentRenderPipeline ?: return@watchFor
            if (inParryMenu) renderPipeline.startParryEffect() else renderPipeline.stopParryEffect()
        }
        gameEvents.watchFor<CardHand.CardDraggedOntoSlotEvent> { loadBulletFromHandInRevolver(it.card, it.slot.num) }
        gameEvents.watchFor<Events.ShootButtonPressed> { if (!isUIFrozen) shoot() }
        gameEvents.watchFor<Events.HolsterButtonPressed> { if (!isUIFrozen) endTurn() }
        gameEvents.watchFor<Events.AfterlifeOpenToggle> {
            // The afterlife opening and closing is handled on the main timeline because it could be important for
            // trigger anims, where the afterlife can open/close automatically
            if (isUIFrozen) {
                FortyFive.soundPlayer.situation("not_allowed", screen)
                return@watchFor
            }
            appendMainTimeline(afterlife.toggleTimeline())
        }
        gameEvents.watchFor<Events.EnemySelected> { (enemy) ->
            targetedEnemy = enemy
        }
        gameEvents.watchFor<Events.CardChangeZoneEvent> { event ->
            if (!event.before) {
                event.card.changeZone(event.newZone, this)
                if (event.newZone == Zone.REVOLVER) event.append {
                    _encounterBehaviours.forEach { (_, behaviour) ->
                        val timeline = behaviour.executeAfterBulletWasPlacedInRevolver(event.card, controller)
                        if (timeline != null) include(timeline)
                    }
                }
            }
            val situation = GameSituation.ZoneChange(event.card, event.oldZone, event.newZone, event.before, event.afterShot)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.CardsDrawnEvent> { event ->
            val situation = GameSituation.CardsDrawn(event.amount, event.isSpecial, event.isFromBottom, event.cards)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.StatusEffectAppliedEvent> { event ->
            val situation = GameSituation.StatusEffectApplied(event.statusEffect, event.toPlayer)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.FullRotationEvent> { event ->
            val situation = GameSituation.CardCompletedFullRotation(event.card)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.CardReplacedEvent> { event ->
            val situation = GameSituation.CardReplaced(event.replaced, event.newCard)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.EndTurnEvent> { event ->
            val situation = GameSituation.TurnEnd
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
                later {
                    encounterBehaviours
                        .mapNotNull { it.executeOnEndTurn() }
                        .collectTimeline()
                        .let { include(it) }
                }
            }
        }
        gameEvents.watchFor<Events.TurnBeginEvent> { event ->
            val situation = GameSituation.TurnBegin
            event.append {
                action {
                    // update ui in case base reserves changed
                    gameEvents.fire(Events.ReservesChanged(
                        curReserves,
                        curReserves,
                        currentTurnStartReserves(),
                        null,
                        controller
                    ))
                }
                include(checkTrigger(situation, event.triggerInformation))
                later {
                    encounterBehaviours
                        .mapNotNull { it.executeOnPlayerTurnStart(controller) }
                        .collectTimeline()
                        .let { include(it) }
                }
            }
        }
        gameEvents.watchFor<Events.RevolverRotatedEvent> { event ->
            if (event.rotation.amount == 0) return@watchFor
            val situation = GameSituation.RevolverRotation(event.rotation)
            event.append {
                _encounterBehaviours.forEach { (_, behaviour) ->
                    val timeline = behaviour.executeAfterRevolverRotated(event.rotation, controller)
                    if (timeline != null) include(timeline)
                }
                include(checkTrigger(situation, event.triggerInformation))
                later {
                    activeEnemies
                        .map { it.executeStatusEffectsAfterRevolverRotation(event.rotation) }
                        .collectTimeline()
                        .let { include(it) }
                }
            }
        }
        gameEvents.watchFor<Events.CardDestroyedEvent> { event ->
            val situation = GameSituation.CardDestroyed(event.card)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.PlayerLivesChanged> { event ->
            val situation = GameSituation.PlayerHealthChanged(event.oldValue, event.newValue, profile.maxHealthInRun!!)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
            }
        }
        gameEvents.watchFor<Events.AfterShotEvent> { event ->
            val situation = GameSituation.AfterShot(event.card)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
                later {
                    _encounterBehaviours.forEach { (_, behaviour) ->
                        val timeline = behaviour.executeAfterRevolverWasShot(event.card, controller)
                        if (timeline != null) include(timeline)
                    }
                }
            }
        }
        gameEvents.watchFor<Events.CardRightClickEvent> { (card) ->
            if (card.rightClickCost == null) return@watchFor
            if (isUIFrozen) return@watchFor
            val triggerInformation = createTriggerInfo(card)
            val situation = GameSituation.CardRightClicked(card)
            appendMainTimeline(Timeline.timeline { later {
                if (card.rightClickCost <= curReserves) {
                    include(card.checkEffects(situation, triggerInformation, controller))
                }
            } })
        }
    }

    private fun checkTrigger(situation: GameSituation, triggerInformation: TriggerInformation): Timeline = createdCards
        .map { it.checkEffects(situation, triggerInformation, this) }
        .collectTimeline()

    private fun updateReserves(newReserves: Int, sourceActor: (() -> Actor)? = null) {
        if (curReserves == newReserves) return
        val prevReserves = curReserves
        curReserves = newReserves
        gameEvents.fire(Events.ReservesChanged(
            prevReserves,
            newReserves,
            currentTurnStartReserves(),
            sourceActor,
            this
        ))
    }

    override fun update() {
        gameEvents.fire(EncounterScreen.UpdateUiEvent(this))
        _encounterBehaviours.removeIf { (predicate, _) -> !predicate(controller) }
        _encounterBehaviours.forEach { it.second.update(controller) }

        animTimelines.forEach(Timeline::updateTimeline)
        mainTimeline.updateTimeline()
        createdCards.forEach { it.update(this) }
        updateStatusEffects()
    }

    private fun initCards() {
        val cards = encounter.forceCards
            ?: encounterContext.forceCards
            ?: profile.currentRunDeck!!.cards

        val stack = mutableListOf<Card>()

        cardPrototypes = ConfigFileManager.loadCards { card ->
            createdCards.add(card)
            encounterBehaviours.forEach { it.initBullet(card, controller) }
            screen.lifetime.tieDisposable(card)
            card.setGame(controller)
        }

        cards.forEach { cardType ->
            stack.add(createCardFromType(cardType))
        }

        if (encounter.shuffleCards) stack.shuffle()
        cardStack.set(stack)

        FortyFive.logger.debug(logTag, "card stack: $stack")

        val onj = ConfigFileManager.getConfigFile("cards")
        defaultBullet = CardType.fromOnj(onj.get<OnjObject>("defaultBullet"))
    }

    override fun destroyCardTimeline(card: Card, sourceCard: Card?): Timeline = Timeline.timeline { later {
        if (!card.inZone(Zone.REVOLVER)) {
            FortyFive.logger.warn(logTag, "cant destroy $card because it isn't in the revolver")
            return@later
        }
        include(card.afterDestroyed(
            card, controller, sourceCard,
            ::putCardInAfterlifeAfterDestroyTimeline,
            ::putCardInHandAfterDestroyTimeline
        ))
    } }

    private fun putCardInHandAfterDestroyTimeline(card: Card, sourceCard: Card?): Timeline = Timeline.timeline {
        val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.HAND, before = true, triggerInfo)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        include(card.presentation.destroyAnimation())
        action { card.presentation.setAlphaZero() }
        action {
            revolver.removeCard(card)
            tryPutCardInHand(card)
            card.presentation.setAlphaOne()
        }
        later {
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
        later {
            val event = Events.CardDestroyedEvent(card, triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    }

    private fun putCardInAfterlifeAfterDestroyTimeline(card: Card, sourceCard: Card?): Timeline = Timeline.timeline {
        val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.AFTERLIFE, before = true, triggerInfo)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        include(card.presentation.destroyAnimation())
        action { card.presentation.setAlphaZero() }
        if (afterlife.isClosed) include(afterlife.openTimeline())
        action {
            revolver.removeCard(card)
            afterlife.pushCard(card)
            card.presentation.setAlphaOne()
        }
        later {
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
        later {
            val event = Events.CardDestroyedEvent(card, triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    }

    override fun putCardsInStackTimeline(
        cardType: CardType,
        amount: Int,
        sourceCard: Card?,
        onTop: Boolean,
    ): Timeline = Timeline.timeline { later {
        repeat(amount) {
            val card = createCardFromType(cardType)
            val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
            val beforeEvent = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.STACK, before = true, triggerInfo)
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action {
                if (onTop){
                    cardStack.addCardAtTop(card)
                } else {
                    cardStack.shuffleCardIntoStack(card, random)
                }
            }
            later {
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    } }

    override fun shuffleCardFromHandIntoStackTimeline(
        card: Card,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        if (card !in cardsInHand) {
            FortyFive.logger.warn(logTag, "Cant shuffle card into the stack because it isn't in the hand $card")
            return@later
        }
        val info = createTriggerInfo(card, sourceCard = sourceCard)
        val event = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.STACK, true, info)
        gameEvents.fire(event)
        include(event.createTimeline())
        include(card.presentation.spawnAnimation(true))
        action {
            cardHand.removeCard(card)
            cardStack.shuffleCardIntoStack(card, random)
        }
        later {
            val animEvent = Events.PlayCardOrbAnimation(card.presentation.animTarget(), true)
            gameEvents.fire(animEvent)
            include(animEvent.orbAnimationTimeline!!)
            val afterEvent = event.copy(before = false)
            includeLater({
                gameEvents.fire(afterEvent)
                afterEvent.createTimeline()
            })
        }
    } }

    override fun tryToPutCardsInHandTimeline(
        cardType: CardType,
        amount: Int,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        val newAmount = maxSpaceInHand(amount)
        if (newAmount == 0) {
            FortyFive.logger.warn(logTag, "Failed to put card in hand because there isn't enough space")
            return@later
        }
        repeat(newAmount) {
            val card = createCardFromType(cardType)
            val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
            val beforeEvent = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.HAND, before = true, triggerInfo)
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action { cardHand.addCard(card) }
            include(card.presentation.spawnAnimation())
            action { checkCardMaximums() }
            later {
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    } }

    override fun bounceBulletTimeline(card: Card): Timeline = Timeline.timeline {

        later  {
            val freeSpace = maxSpaceInHand()
            var leaveInRevolver = freeSpace == 0
            val triggerInformation = createTriggerInfo(card)
            val beforeEvent = Events.CardChangeZoneEvent(
                card,
                Zone.REVOLVER,
                Zone.HAND,
                true,
                triggerInformation
            )
            if (!leaveInRevolver) {
                gameEvents.fire(beforeEvent)
                include(beforeEvent.createTimeline())
            }
            if (!leaveInRevolver) later {
                include(card.presentation.spawnAnimation(reverse = true))
                action { card.presentation.setAlphaZero() }
            }
            if (!leaveInRevolver) later {
                val success = maxSpaceInHand() > 0
                if (success) revolver.removeCard(card)
                require(tryPutCardInHand(card))
                if (!success) leaveInRevolver = true
                if (success) {
                    delay(200)
                    include(card.presentation.spawnAnimation())
                    action { card.presentation.setAlphaOne() }
                    delay(100)
                } else {
                    action { card.presentation.setAlphaOne() }
                }
            }
            later {
                val afterEvent = Events.CardChangeZoneEvent(
                    card,
                    Zone.REVOLVER,
                    Zone.HAND,
                    false,
                    triggerInformation
                )
                if (!leaveInRevolver) {
                    gameEvents.fire(afterEvent)
                    include(afterEvent.createTimeline())
                }
            }
        }
    }

    override fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        var newRotation = if (ignoreEncounterModifiers) {
            rotation
        } else {
            encounterBehaviours.fold(rotation) { acc, cur -> cur.modifyRevolverRotation(acc) }
        }
        playerStatusEffects.forEach { newRotation = it.modifyRevolverRotation(newRotation) }
        if (newRotation.amount == 0) return@later
        include(revolver.rotate(newRotation))
        action {
            revolverRotationCounter += newRotation.amount
        }
        val fullRotationTimelineCreator = { card: Card -> Timeline.timeline {
            later {
                val info = createTriggerInfo(card, sourceCard = sourceCard)
                val event = Events.FullRotationEvent(card, info)
                gameEvents.fire(event)
                include(event.createTimeline())
            }
        } }
        later {
            cardsInRevolver()
                .map { it.onRevolverRotationTimeline(newRotation, fullRotationTimelineCreator) }
                .collectTimeline()
                .let { include(it) }
        }
        later {
            val info = createTriggerInfo(null, sourceCard = sourceCard)
            val event = Events.RevolverRotatedEvent(rotation, info)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    } }

    override fun drawCardsTimeline(
        amount: Int,
        isSpecial: Boolean,
        fromBottom: Boolean,
        sourceCard: Card?,
    ): Timeline = Timeline.timeline { later {

        var cardsToDraw = amount
        cardsToDraw += encounterBehaviours.sumOf {
            if (isSpecial) it.additionalCardsToDrawInSpecialDraw() else it.additionalCardsToDrawInNormalDraw()
        }
        cardsToDraw = floor(
            encounterBehaviours
                .fold(cardsToDraw.toFloat()) { acc, cur ->
                    acc * (if (isSpecial) cur.cardsInSpecialDrawMultiplier() else cur.cardsInNormalDrawMultiplier())
                }
        ).toInt()
        cardsToDraw = maxSpaceInHand(cardsToDraw)

        val cardAcc = mutableListOf<Card>()
        repeat(cardsToDraw) {
            include(drawCardTimeline(fromBottom, sourceCard, cardAcc))
        }
        later {
            if (cardsToDraw <= 0) return@later
            val info = createTriggerInfo(
                null,
                amountOfCardsDrawn = cardsToDraw,
                sourceCard = sourceCard
            )
            val event = Events.CardsDrawnEvent(cardsToDraw, isSpecial, fromBottom, cardAcc, info)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    } }

    private fun drawCardTimeline(
        fromBottom: Boolean,
        sourceCard: Card?,
        cardAcc: MutableList<Card>? = null
    ): Timeline = Timeline.timeline {
        var card: Card? = null
        action {
            card = cardStack.drawCard(fromBottom)
            cardsDrawn++
            card?.let { cardAcc?.add(it) }
        }
        later {
            var card = card
            if (card == null) {
                // create default card and put in stack
                val info = createTriggerInfo(card, sourceCard = sourceCard)
                card = createCardFromType(defaultBullet)
                val limboEventBefore = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.STACK, before = true, info)
                val limboEventAfter = limboEventBefore.copy(before = false)
                gameEvents.fire(limboEventBefore)
                include(limboEventBefore.createTimeline())
                action { cardStack.addCardAtBottom(card) }
                includeLater({
                    gameEvents.fire(limboEventAfter)
                    limboEventAfter.createTimeline()
                })
            }
            include(putCardFromStackInHandTimeline(card, sourceCard))
        }
    }

    override fun createBulletsInAfterlifeTimeline(cardType: CardType, amount: Int, sourceCard: Card?): Timeline = Timeline.timeline {
        later {
            if (afterlife.isClosed) include(afterlife.openTimeline())
        }
        repeat(amount) {
            later {
                val card = createCardFromType(cardType)
                val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
                val beforeEvent = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.AFTERLIFE, true, triggerInfo)
                gameEvents.fire(beforeEvent)
                include(beforeEvent.createTimeline())
                later {
                    afterlife.pushCard(card)
                    val afterEvent = beforeEvent.copy(before = false)
                    gameEvents.fire(afterEvent)
                    include(afterEvent.createTimeline())
                }
            }
        }
    }

    override fun descendBulletTimeline(): Timeline = Timeline.timeline { later {
        val card = afterlife.cards.firstOrNull() ?: return@later
        val info = createTriggerInfo(card)
        val event = Events.CardChangeZoneEvent(card, Zone.AFTERLIFE, Zone.LIMBO, true, info)
        gameEvents.fire(event)
        include(event.createTimeline())
        include(afterlife.scrollToBeginTimeline())
        delay(200)
        include(card.presentation.descendAnimation())
        delay(50)
        later {
            val damage = card.curOnShotDamage(controller)
            card
                .targetedEnemies(controller)
                .map { it.damage(damage * 2) }
                .collectTimeline()
                .let { include(it) }
        }
        delay(400)
        include(afterlife.popCardTimeline())
        action { card.presentation.resetDescendAnimation() }
        delay(400)
        later {
            val afterEvent = event.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
    } }

    override fun resurrectTimeline(intoSlot: Int): Timeline = Timeline.timeline {
        val slot = revolver.slots[intoSlot - 1]
        later {
            if (slot.card != null) {
                FortyFive.logger.warn(logTag, "cant resurrect card into $intoSlot because it has a card")
                return@later
            }
            val card = afterlife.cards.firstOrNull() ?: return@later
            val info = createTriggerInfo(card)
            val event = Events.CardChangeZoneEvent(card, Zone.AFTERLIFE, Zone.REVOLVER, true, info)
            gameEvents.fire(event)
            include(event.createTimeline())
            include(afterlife.scrollToBeginTimeline())
            delay(200)
            include(card.presentation.spawnAnimation(reverse = true))
            action { card.presentation.setAlphaZero() }
            delay(200)
            later {
                if (slot.card != null) { // may have changed due to bullet effects
                    card.presentation.setAlphaOne()
                    return@later
                }
                include(afterlife.popCardTimeline())
                action { revolver.setCard(slot.num, card) }
                include(card.presentation.spawnAnimation())
                action { card.presentation.setAlphaOne() }
            }
            later {
                val afterEvent = event.copy(before = false)
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    }

    override fun switchSlotOfBulletInRevolverTimeline(
        card: Card,
        newSlot: Int,
    ): Timeline = Timeline.timeline {
        action {
            val currentSlot = revolver
                .slots
                .find { it.card === card }
            requireNotNull(currentSlot) { "card $card not in revolver" }
            revolver.removeCard(currentSlot.num)
            requireNull(revolver.getCardInSlot(newSlot)) { "cant switch slot of bullet to a used one" }
            revolver.setCard(newSlot, card)
        }
    }

    override fun putCardFromStackInHandTimeline(
        card: Card,
        source: Card?,
    ): Timeline = Timeline.timeline {
        var orbAnimationTimeline: Timeline? = null
        val info = createTriggerInfo(card, sourceCard = source)
        var canAdd = true
        action { if (maxSpaceInHand() == 0) canAdd = false }
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.STACK, Zone.HAND, before = true, info)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        }, { canAdd })
        action {
            if (maxSpaceInHand() == 0) canAdd = false
            if (canAdd) cardStack.remove(card)
            if (!canAdd) return@action
            require(tryPutCardInHand(card))
            card.presentation.setAlphaZero()
            val event = Events.PlayCardOrbAnimation(card.presentation.animTarget())
            gameEvents.fire(event)
            orbAnimationTimeline = event.orbAnimationTimeline
        }
        later {
            if (orbAnimationTimeline != null && canAdd) {
                include(orbAnimationTimeline!!)
                action { card.presentation.setAlphaOne() }
                include(card.presentation.spawnAnimation())
            }
        }
        includeLater({
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            afterEvent.createTimeline()
        }, { canAdd })
        action { checkCardMaximums() }
    }

    private fun maxSpaceInHand(desiredSpace: Int = Int.MAX_VALUE): Int =
        (Config.hardMaxCards - cardHand.amountOfCards).between(0, desiredSpace)

    override fun tryApplyStatusEffectToEnemyTimeline(
        statusEffect: StatusEffect,
        enemy: Enemy,
        source: Card?,
    ): Timeline = Timeline.timeline { later {
        if (encounterBehaviours.any { !it.shouldApplyStatusEffects() }) {
            FortyFive.logger.debug(logTag, "cant apply status effect because they are disabled")
            return@later
        }
        enemy.applyEffect(statusEffect, controller)
        if (!inEnemyPhase && statusEffect.reevaluateEnemyAttack()) {
            enemy.reevaluateAction(controller)
        }
        val info = createTriggerInfo(null, sourceCard = source)
        val event = Events.StatusEffectAppliedEvent(statusEffect, false, info)
        gameEvents.fire(event)
        include(event.createTimeline())
        val situation = GameSituation.EnemyStatusEffectsChanged(enemy, statusEffect, true)
        include(checkTrigger(situation, info))
    } }

    override fun damagePlayerTimeline(
        damage: Int,
        triggeredByStatusEffect: Boolean,
        isPiercing: Boolean
    ): Timeline = Timeline.timeline { later {
        val newDamage = if (isPiercing) {
            damage
        } else {
            _playerStatusEffects.fold(damage) { acc, cur -> cur.modifyDamage(acc) }
        }
        if (newDamage != damage) {
            val event = Events.PlayShieldAnimation(controller)
            gameEvents.fire(event)
            include(event.shieldTimeline!!)
        }
        if (newDamage == 0) return@later
        include(updatePlayerLivesTimeline(curPlayerLives - newDamage))
        action {
            FortyFive.soundPlayer.situation("enemy_attack", controller.screen)
            val event = Events.PlayPlayerDamagedEffects(controller)
            gameEvents.fire(event)
            dispatchAnimTimeline(event.animationTimeline!!)
            FortyFive.logger.debug(
                logTag,
                "player got damaged; damage = $newDamage; curPlayerLives = $curPlayerLives"
            )
        }
        includeLater(
            { playerDeathTimeline() },
            { curPlayerLives <= 0 }
        )
        includeLater(
            {
                _playerStatusEffects
                    .mapNotNull { it.executeAfterDamage(newDamage, StatusEffectTarget.PlayerTarget) }
                    .collectTimeline()
            },
            { !triggeredByStatusEffect && newDamage > 0}
        )
    } }

    private fun updatePlayerLivesTimeline(newValue: Int, source: Card? = null): Timeline = Timeline.timeline { later {
        val old = curPlayerLives
        curPlayerLives = newValue
        includeLater({
            val info = createTriggerInfo(null, sourceCard = source)
            val event = Events.PlayerLivesChanged(old, newValue, info)
            gameEvents.fire(event)
            event.createTimeline()
        })
    } }

    override fun playerDeathTimeline(): Timeline = Timeline.timeline {
        action {
            FortyFive.logger.debug(logTag, "player lost")
            animTimelines.forEach(Timeline::stopTimeline)
        }
        include(FortyFive.currentRenderPipeline!!.getFadeToBlackTimeline(2000, stayBlack = true))
        delay(500)
        action {
            if (profile.isRunActive) {
                profile.loseRun()
                FortyFive.screenManager.ensureNextScreen(LoseRunScreen)
            }
            FortyFive.screenManager.overrideNextTransition(ScreenManager.ScreenTransition(null, null))
            FortyFive.screenManager.screenFinished()
        }
    }

    override fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect, source: Card?): Timeline = Timeline.timeline {
        later {
            FortyFive.logger.debug(logTag, "status effect $effect applied to player")
            var stacked = false
            _playerStatusEffects
                .find { it.canStackWith(effect) }
                ?.let {
                    FortyFive.logger.debug(logTag, "stacked with $it")
                    it.stack(effect)
                    stacked = true
                }
            if (!stacked) {
                effect.start(controller)
                _playerStatusEffects.add(effect)
                gameEvents.fire(Events.AddedPlayerStatusEffect(effect)) // separate event for UI purposes
            }
            val info = createTriggerInfo(null, sourceCard = source)
            val event = Events.StatusEffectAppliedEvent(effect, true, info)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    }

    private fun updateStatusEffects() {
        _playerStatusEffects.iterateRemoving { effect, remover ->
            if (effect.isStillValid()) return@iterateRemoving
            remover()
            gameEvents.fire(Events.RemovedPlayerStatusEffect(effect))
        }
    }

    override fun destroyCardInHandTimeline(card: Card, sourceCard: Card?): Timeline = Timeline.timeline { later {
        if (card !in cardHand.allCards()) {
            FortyFive.logger.warn(logTag, "cant destroy $card because it isn't in the hand")
            return@later
        }
        val info = createTriggerInfo(card, sourceCard = sourceCard)
        val event = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.AFTERLIFE, true, info)
        gameEvents.fire(event)
        include(event.createTimeline())
        include(card.presentation.destroyAnimation())
        action { card.presentation.setAlphaZero() }
        includeLater({ afterlife.openTimeline() }, { afterlife.isClosed })
        later {
            cardHand.removeCard(card)
            afterlife.pushCard(card)
            card.presentation.setAlphaOne()
            checkCardMaximums()
            val afterEvent = event.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
    } }

    override fun removeAllPlayerStatusEffectsTimeline(): Timeline = Timeline.timeline {
        action {
            _playerStatusEffects.iterateRemoving { effect, remover ->
                remover()
                gameEvents.fire(Events.RemovedPlayerStatusEffect(effect))
            }
        }
    }

    private fun parryTimeline(
        damage: Int,
        isPiercing: Boolean,
        card: Card
    ): Timeline = Timeline.timeline { later {
        FortyFive.soundPlayer.situation("enter_parry", controller.screen)
        val damageToParry = card.curParryValue(controller)
        val remainingDamage = if (card.isReinforced) 0 else (damage - damageToParry).coerceAtLeast(0)
        val parryEnterEvent = Events.ParryStateChange(true, damage, damageToParry)
        val parryLeaveEvent = Events.ParryStateChange(false, 0, 0)
        parryEnterEvent.resolutionPromise.then { gameEvents.fire(parryLeaveEvent) }
        include(afterlife.closeTimeline())
        action { gameEvents.fire(parryEnterEvent) }
        delayUntil { parryEnterEvent.resolutionPromise.isResolved }
        later {
            val parried = parryEnterEvent.resolutionPromise.getOrError()
            if (parried) {
                if (remainingDamage > 0) {
                    include(damagePlayerTimeline(remainingDamage, false, isPiercing))
                }
                val triggerInfo = createTriggerInfo(card, isOnShot = true)
                include(card.afterShot(
                    controller, true, damage,
                    triggerInfo,
                    ::putCardBackInHandAfterShot, ::putCardInTheStackAfterShot
                ))
                include(rotateRevolverTimeline(card.getRotationDirection(controller)))
            } else {
                include(damagePlayerTimeline(damage, false, isPiercing))
            }
        }
    } }

    override fun enemyAttackTimeline(
        damage: Int,
        enemy: Enemy,
        isPiercing: Boolean
    ): Timeline = Timeline.timeline { later {
        val card = revolver.getCardInSlot(5)
        if (card == null) {
            include(damagePlayerTimeline(damage, false, isPiercing))
        } else {
            include(parryTimeline(damage, isPiercing, card))
        }
        later { enemy.statusEffects.forEach { it.onEnemyAttack() } }
    } }

    override fun putBulletFromRevolverUnderTheStackTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    private fun putCardBackInHandAfterShot(card: Card, triggerInfo: TriggerInformation): Timeline = Timeline.timeline {
        later  {
            val freeSpace = maxSpaceInHand()
            var putInStackInstead = freeSpace == 0
            val beforeEvent = Events.CardChangeZoneEvent(
                card,
                Zone.REVOLVER,
                if (putInStackInstead) Zone.STACK else Zone.HAND,
                true,
                triggerInfo,
                true
            )
            gameEvents.fire(beforeEvent)
            include(beforeEvent.createTimeline())
            if (!putInStackInstead) later {
                include(card.presentation.spawnAnimation(reverse = true))
                action { card.presentation.setAlphaZero() }
            }
            action { revolver.removeCard(card) }
            if (!putInStackInstead) later {
                val success = tryPutCardInHand(card)
                if (!success) putInStackInstead = true
                if (success) {
                    delay(200)
                    include(card.presentation.spawnAnimation())
                    action { card.presentation.setAlphaOne() }
                    delay(100)
                } else {
                    action { card.presentation.setAlphaOne() }
                }
            }
            later {
                if (putInStackInstead) cardStack.addCardAtBottom(card)
                val afterEvent = Events.CardChangeZoneEvent(
                    card,
                    Zone.REVOLVER,
                    if (putInStackInstead) Zone.STACK else Zone.HAND,
                    false,
                    triggerInfo,
                    true
                )
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    }

    private fun tryPutCardInHand(card: Card): Boolean {
        val spaceInHand = maxSpaceInHand(1)
        if (spaceInHand == 0) return false
        cardHand.addCard(card)
        checkCardMaximums()
        return true
    }

    private fun putCardInTheStackAfterShot(card: Card, triggerInfo: TriggerInformation): Timeline = Timeline.timeline {
        val beforeEvent = Events.CardChangeZoneEvent(
            card,
            Zone.REVOLVER,
            Zone.STACK,
            before = true,
            triggerInfo,
            afterShot = true
        )
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        action {
            revolver.removeCard(card)
            cardStack.addCardAtBottom(card)
        }
        later {
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
    }

    private fun shootTimeline(): Timeline = Timeline.timeline { later {

        if (encounterBehaviours.any { !it.canShootRevolver(controller) }) return@later
        val cardToShoot = revolver.getCardInSlot(5)
        val rotationDirection = cardToShoot?.getRotationDirection(controller) ?: RevolverRotation.Right(1)

        FortyFive.logger.debug(logTag,
            "revolver is shooting;" +
                    "cardToShoot = $cardToShoot"
        )

        if (cardToShoot?.canBeShot(controller)?.not() ?: false) {
            FortyFive.logger.debug(logTag, "Card can't be shot because it blocks")
            return@later
        }

        val targetedEnemies = cardToShoot?.targetedEnemies(controller) ?: listOf(targetedEnemy)

        val triggerInfo = TriggerInformation(
            controller = controller,
            targetedEnemies = targetedEnemies,
            sourceCard = cardToShoot,
            isOnShot = true,
        )

        action {
            FortyFive.soundPlayer.situation("revolver_shot", screen)
            val postProcessor = FortyFive.currentRenderPipeline!!.getOnShotPostProcessingTimeline()
            dispatchAnimTimeline(postProcessor)
        }
        cardToShoot?.let { card ->
            targetedEnemies
                .map { it.damage(cardToShoot.curOnShotDamage(controller), isPiercing = cardToShoot.isPiercing) }
                .collectTimeline()
                .let { include(it) }
            // Not handled via event because things like encounter modifiers or
            // status effects shouldn't hook into here
            include(checkTrigger(GameSituation.OnShot(card), triggerInfo))
            include(card.afterShot(
                controller, false, 0,
                triggerInfo,
                ::putCardBackInHandAfterShot, ::putCardInTheStackAfterShot
            ))
        }
        include(rotateRevolverTimeline(rotationDirection))
        includeLater(
            { damagePlayerTimeline(Config.shotEmptyDamage) },
            { cardToShoot == null }
        )

        if (cardToShoot != null) later {
            val afterShotInfo = TriggerInformation(
                controller = controller,
                targetedEnemies = targetedEnemies,
                sourceCard = cardToShoot,
            )
            val event = Events.AfterShotEvent(cardToShoot, afterShotInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
        later {
            _playerStatusEffects
                .mapNotNull { it.executeAfterShot() }
                .collectTimeline()
                .let { include(it) }
            allEnemies
                .map { it.executeStatusEffectsAfterShot() }
                .collectTimeline()
                .let { include(it) }
        }
    } }

    override fun shoot() {
        appendMainTimeline(shootTimeline())
    }

    override fun gainReserves(amount: Int, source: (() -> Actor)?) {
        updateReserves(curReserves + amount, source)
    }

    override fun tryPay(cost: Int, animTarget: (() -> Actor)?): Boolean {
        if (cost > curReserves) return false
        updateReserves(curReserves - cost, sourceActor = animTarget)
        return true
    }

    private fun checkCardMaximums() {
        val cards = cardHand.amountOfCards
        when {
            cards < Config.softMaxCards -> {
                if (softMaxCardsWarning.isActive) softMaxCardsWarning.hide()
                if (hardMaxCardsWarning.isActive) hardMaxCardsWarning.hide()
            }
            cards < Config.hardMaxCards -> {
                if (!softMaxCardsWarning.isActive) softMaxCardsWarning.show()
                if (hardMaxCardsWarning.isActive) hardMaxCardsWarning.hide()
            }
            cards >= Config.hardMaxCards -> {
                if (softMaxCardsWarning.isActive) softMaxCardsWarning.hide()
                if (!hardMaxCardsWarning.isActive) hardMaxCardsWarning.show()
            }
        }
    }

    override fun addTemporaryEncounterBehaviour(
        behaviour: EncounterBehaviour,
        validityChecker: (GameController) -> Boolean
    ) {
        FortyFive.logger.debug(logTag, "added temporary encounter behaviour $behaviour")
        _encounterBehaviours.add(validityChecker to behaviour)
        behaviour.onStart(controller)
        // No event in this case, because temporary encounter modifiers aren't displayed
    }

    override fun addEncounterBehaviour(behaviour: EncounterBehaviour) {
        val alwaysTrue = { _: GameController -> true }
        _encounterBehaviours.add(alwaysTrue to behaviour)
        behaviour.onStart(controller)
    }

    override fun addEncounterModifier(modifier: EncounterModifier) {
        _encounterModifiers.add(modifier)
        modifier.behaviours().forEach { addEncounterBehaviour(it) }
        gameEvents.fire(Events.EncounterModifierAdded(modifier))
    }

    private fun addTalisman(talisman: Talisman) {
        // TODO: display
        talisman.behaviours().forEach { addEncounterBehaviour(it) }
    }

    override fun initEnemyArea(enemies: List<Enemy>) {
    }

    override fun playGameAnimation(anim: GameAnimation) {
        dispatchAnimTimeline(Timeline.timeline {
            action { anim.start() }
            delayUntil { anim.update(); anim.isFinished() }
            action { anim.end() }
        })
    }

    override fun loadBulletFromHandInRevolver(card: Card, slot: Int) {
        if (isUIFrozen) return
        val timeline = if (card.isPunk) {
            loadPunkBulletFromHandInRevolverTimeline(card, slot)
        } else {
            loadBulletFromHandInRevolverTimeline(card, slot)
        }
        appendMainTimeline(timeline)
    }

    private fun loadBulletFromHandInRevolverTimeline(card: Card, slot: Int): Timeline = Timeline.timeline {
        var skip = false
        var cardInSlot: Card? = null
        val info = createTriggerInfo(card, sourceCard = card)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.REVOLVER, before = true, info)
        later {
            action {
                FortyFive.logger.debug(logTag, "attempting to load bullet $card in revolver slot $slot")
                cardInSlot = revolver.getCardInSlot(slot)
                val blockedByCard = cardInSlot != null && !cardInSlot!!.canBeReplaced(controller, card)
                val shouldSkip = !card.allowsEnteringGame(controller, slot)
                        || blockedByCard
                        || !tryPay(card.baseCost, card.presentation.animTarget())
                if (!shouldSkip) return@action
                FortyFive.soundPlayer.situation("not_allowed", screen)
                skip = true
            }
        }
        later {
            if (skip) return@later
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action {
                cardHand.removeCard(card)
                if (cardInSlot != null) revolver.preAddCard(slot, card)
                checkCardMaximums()
            }
            later {
                if (cardInSlot == null) return@later
                include(cardInSlot!!.replaceTimeline(controller, card))
                val info = createTriggerInfo(cardInSlot, sourceCard = card)
                val event = Events.CardReplacedEvent(cardInSlot!!, card, info)
                includeLater({
                    gameEvents.fire(event)
                    event.createTimeline()
                })
            }
            action {
                revolver.setCard(slot, card)
            }
            includeLater({
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                afterEvent.createTimeline()
            })
        }
    }

    private fun loadPunkBulletFromHandInRevolverTimeline(card: Card, slot: Int): Timeline = Timeline.timeline {
        later {
            var cardInSlot: Card? = null
            val info = createTriggerInfo(card, sourceCard = card)
            val beforeEvent = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.REVOLVER, before = true, info)
            action {
                FortyFive.logger.debug(logTag, "loading punk bullet $card in revolver slot $slot")
                tryPay(card.baseCost, card.presentation.animTarget()) // ignore return value
                cardInSlot = revolver.getCardInSlot(slot)
            }
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action {
                cardHand.removeCard(card)
                if (cardInSlot != null) revolver.preAddCard(slot, card)
                checkCardMaximums()
            }
            later {
                if (cardInSlot == null) return@later
                include(destroyCardTimeline(cardInSlot!!, card))
            }
            action {
                revolver.setCard(slot, card)
            }
            includeLater({
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                afterEvent.createTimeline()
            })
        }
    }

    private fun bannerAnimationTimeline(isPlayer: Boolean): Timeline {
        val event = Events.PlayBannerAnimation(controller, isPlayer)
        gameEvents.fire(event)
        return event.timeline!!
    }

    private fun enemyActionTimeline(): Timeline = Timeline.timeline {
        activeEnemies.forEach { enemy -> later {
            val action = enemy.resolveAction(controller, 1.0) ?: return@later
            if (action.prototype.hasSpecialAnimation) {
                val event = Events.PlayEnemySpecialAttackAnim(controller, action)
                gameEvents.fire(event)
                delay(300)
                include(event.createTimeline())
                delayUntil { event.finishedPromise.isResolved }
                delay(100)
                include(action.getTimeline())
                delay(400)
            } else {
                val event = Enemy.PlayChargeAnimationEvent()
                enemy.enemyEvents.fire(event)
                action {
                    event.timeline.getOrNull()?.let { dispatchAnimTimeline(it) }
                }
                delay(200)
                include(action.getTimeline())
                delay(400)
            }
            action {
                val event = Enemy.EnemyActionChangedEvent(NextEnemyAction.None)
                enemy.enemyEvents.fire(event)
            }
        } }
    }

    private fun winTimeline(): Timeline = Timeline.timeline { later {
        val money = -allEnemies.sumOf { it.currentHealth }
        val playerGetsCard = !encounter.special &&
                !encounterContext.isExtraction &&
                Utils.coinFlip(Config.playerGetsRewardCardChance, random)
        val event = Events.ShowPlayerWonPopup(
            gotCard = playerGetsCard,
            cashAmount = money
        )
        action {
            gameEvents.fire(event)
            FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.MAIN, 5_000)
        }
        delayUntil { event.popupPromise.isResolved }
        if (money > 0) {
            delay(600)
            action {
                FortyFive.soundPlayer.situation("money_earned", controller.screen)
                profile.earnMoney(money)
            }
        }
        delay(300)
        action {
            encounterContext.completed()
            profile.write()

            val chooseCardContext = object : ChooseCardScreenContext {
                override var seed: Long = TimeUtils.millis()
                override val nbrOfCards: Int = 3
                override val types: List<String> = listOf()
                override val enableRerolls: Boolean = true
                override var amountOfRerolls: Int = 0
                override val rerollPriceIncrease: Int = Config.rewardRerollPriceIncrease
                override val rerollBasePrice: Int = Config.rewardRerollBasePrice

                override fun completed() { }
            }

            if (playerGetsCard) {
                FortyFive.screenManager.ensureNextScreen(ChooseCardScreen, chooseCardContext)
            }

            if (encounterContext.isExtraction) {
                FortyFive.screenManager.ensureNextScreen(WinRunScreen)
            }

            FortyFive.screenManager.screenFinished()
        }
    } }

    private fun endTurnTimeline(): Timeline = Timeline.timeline { later {
        action { FortyFive.soundPlayer.situation("end_turn", screen) }

        if (hasWon) {
            include(winTimeline())
            return@later
        }

        later {
            activeEnemies
                .map { it.executeStatusEffectsAfterTurn() }
                .collectTimeline()
                .let { include(it) }
        }

        later {
            playerStatusEffects
                .mapNotNull { it.executeOnNewTurn(StatusEffectTarget.PlayerTarget) }
                .collectTimeline()
                .let { include(it) }
        }

        later {
            val triggerInfo = createTriggerInfo(null)
            val event = Events.EndTurnEvent(triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }

        later {
            if (cardHand.amountOfCards <= Config.softMaxCards) return@later

            val callback: (card: Card) -> Unit = { card ->
                val info = createTriggerInfo(card)
                val event = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.STACK, true, info)
                gameEvents.fire(event)
                cardHand.removeCard(card)
                checkCardMaximums()
            }
            val event = Events.PutCardsUnderStack(cardHand.amountOfCards - Config.softMaxCards, callback)
            action { gameEvents.fire(event) }
            later {
                delayUntil { event.selectedCards.isResolved }
                later {
                    val selectedCards = event.selectedCards.getOrError()
                    selectedCards.forEach { card ->
                        val info = createTriggerInfo(card)
                        // TODO: figure out how to do the early action
                        val zoneChangeEvent = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.STACK, false, info)
                        includeLater({
                            gameEvents.fire(zoneChangeEvent)
                            zoneChangeEvent.createTimeline()
                        })
                        cardStack.addCardAtBottom(card)
                    }
                }
            }
        }

        include(bannerAnimationTimeline(false))
        action { inEnemyPhase = true }
        include(enemyActionTimeline())
        action { inEnemyPhase = false }
        include(bannerAnimationTimeline(true))

        action {
            turnCounter++
        }

        action {
            chooseEnemyActions()
            FortyFive.soundPlayer.situation("turn_begin", screen)
            updateReserves(currentTurnStartReserves(), { revolver.forceGetActor() })
        }

        includeLater({ drawCardsTimeline(Config.cardsToDraw) })

        later {
            val triggerInfo = createTriggerInfo(null)
            val event = Events.TurnBeginEvent(triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    } }

    private fun currentTurnStartReserves(): Int = encounterBehaviours.fold(Config.baseReserves) { acc, cur ->
        cur.modifyReserves(controller, acc)
    }

    private fun endTurn() {
        appendMainTimeline(endTurnTimeline())
    }

    private fun chooseEnemyActions() {
        val otherActions = mutableListOf<Pair<EnemyActionPrototype, Boolean>>()
        activeEnemies.forEach { enemy ->
            val action = enemy.chooseNewAction(this, enemyDifficulty.toDouble(), otherActions)
            action?.let { otherActions.add(it) }
        }
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

    override fun targetedEnemy(): Enemy = targetedEnemy

    override fun slotOfCard(card: Card): Int? = revolver.slots.find { it.card === card }?.num

    override fun titleOfCard(cardType: CardType): String = cardPrototypes
        .find { it.name == cardType.name }
        ?.title
        ?: throw RuntimeException("No card with name $cardType")

    override fun createCardFromType(cardType: CardType): Card = cardPrototypes
        .find { it.name == cardType.name }
        ?.create(screen, cardType, cardPresentationProvider)
        ?: throw RuntimeException("no card '$cardType'")

    private fun createTriggerInfo(
        card: Card?,
        multiplier: Int? = null,
        isOnShot: Boolean = false,
        amountOfCardsDrawn: Int = 0,
        sourceCard: Card? = null,
    ): TriggerInformation = TriggerInformation(
        controller = this,
        targetedEnemies = card?.targetedEnemies(controller) ?: listOf(targetedEnemy),
        multiplier = multiplier,
        isOnShot = isOnShot,
        amountOfCardsDrawn = amountOfCardsDrawn,
        sourceCard = sourceCard
    )

    override fun toString(): String = "GameController"

    companion object {
        const val logTag = "GameController"
    }

    enum class Zone {
        STACK, HAND, REVOLVER, AFTERLIFE, LIMBO
    }

    object Config {
//        const val baseReserves = 20
        const val baseReserves = 4
        const val softMaxCards = 12
        const val hardMaxCards = 20
        const val cardsToDrawInFirstRound = 6
        const val cardsToDraw = 2
        const val shotEmptyDamage = 5
        const val playerGetsRewardCardChance = 1f
        const val rewardRerollPriceIncrease = 30
        const val rewardRerollBasePrice = 30
    }

    object Events {
        data class ReservesChanged(
            val old: Int,
            val new: Int,
            val base: Int,
            val sourceActor: (() -> Actor)? = null,
            val controller: GameController
        )
        data class PlayCardOrbAnimation(
            val targetActor: () -> Actor,
            val reverse: Boolean = false,
            var orbAnimationTimeline: Timeline? = null
        )
        data class ParryStateChange(
            val inParryMenu: Boolean,
            val damage: Int,
            val ableToBlock: Int,
            val resolutionPromise: Promise<Boolean /*= parried*/> = Promise()
        )
        data class SelectionChangedEvent(val text: String?)
        data class SetupEnemies(val enemies: List<Enemy>)
        data class EncounterModifierAdded(val modifier: EncounterModifier)
        data class EnemySelected(val selected: Enemy)
        data class PutCardsUnderStack(
            val amount: Int,
            val cardAddedCallback: (card: Card) -> Unit,
            val selectedCards: Promise<List<Card>> = Promise()
        )
        data class ShowPlayerWonPopup(
            val gotCard: Boolean,
            val cashAmount: Int,
            val popupPromise: Promise<Unit> = Promise()
        )
        data class AddedPlayerStatusEffect(val statusEffect: StatusEffect)
        data class RemovedPlayerStatusEffect(val statusEffect: StatusEffect)
        data object ShootButtonPressed
        data object HolsterButtonPressed
        data object AfterlifeOpenToggle
        data class CardRightClickEvent(val card: Card)

        data class PlayPlayerDamagedEffects(val controller: GameController, var animationTimeline: Timeline? = null)
        data class PlayShieldAnimation(val controller: GameController, var shieldTimeline: Timeline? = null)
        data class PlayBannerAnimation(
            val controller: GameController,
            val isPlayer: Boolean,
            var timeline: Timeline? = null
        )
        data class SteelNervesCountdown(val newNumber: Int)

        abstract class TimelineBuildingEvent {

            val dsl = Timeline.TimelineBuilderDSL()

            inline fun append(block: Timeline.TimelineBuilderDSL.() -> Unit) {
                block(dsl)
            }

            fun createTimeline(): Timeline = dsl.build()
        }

        data class CardChangeZoneEvent(
            val card: Card,
            val oldZone: Zone,
            val newZone: Zone,
            val before: Boolean,
            val triggerInformation: TriggerInformation,
            val afterShot: Boolean = false,
        ) : TimelineBuildingEvent()

        data class CardsDrawnEvent(
            val amount: Int,
            val isSpecial: Boolean,
            val isFromBottom: Boolean,
            val cards: List<Card>,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class RevolverRotatedEvent(
            val rotation: RevolverRotation,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class StatusEffectAppliedEvent(
            val statusEffect: StatusEffect,
            val toPlayer: Boolean,
            val triggerInformation: TriggerInformation,
        ) : TimelineBuildingEvent()

        data class AfterShotEvent(
            val card: Card,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class EndTurnEvent(
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class TurnBeginEvent(
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class CardDestroyedEvent(
            val card: Card,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class FullRotationEvent(
            val card: Card,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class CardReplacedEvent(
            val replaced: Card,
            val newCard: Card,
            val triggerInformation: TriggerInformation,
        ) : TimelineBuildingEvent()

        data class PlayerLivesChanged(
            val oldValue: Int,
            val newValue: Int,
            val triggerInformation: TriggerInformation
        ) : TimelineBuildingEvent()

        data class PlayEnemySpecialAttackAnim(
            val controller: GameController,
            val enemyAction: EnemyAction,
            val finishedPromise: Promise<Unit> = Promise(),
        ) : TimelineBuildingEvent()
    }
}

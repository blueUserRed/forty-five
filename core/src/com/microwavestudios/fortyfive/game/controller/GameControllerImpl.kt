package com.microwavestudios.fortyfive.game.controller

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.card.*
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.enemy.EnemyAction
import com.microwavestudios.fortyfive.game.enemy.NextEnemyAction
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.rendering.GameRenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.SoundPlayer
import com.microwavestudios.fortyfive.game.widgets.Afterlife
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.game.widgets.CardHand
import com.microwavestudios.fortyfive.game.widgets.Revolver
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.run.RunGeneratorConfig
import com.microwavestudios.fortyfive.screen.Inject
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreen
import com.microwavestudios.fortyfive.screen.screens.ChooseCardScreenContext
import com.microwavestudios.fortyfive.screen.screens.LoseRunScreen
import com.microwavestudios.fortyfive.screen.screens.WinRunScreen
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import kotlin.collections.map
import kotlin.math.floor

class GameControllerImpl(
    override val screen: OnjScreen,
    override val gameEvents: EventPipeline,
    private val warningParent: WarningParent,
    override val afterlife: Afterlife,
) : ScreenController(), GameController, ResourceBorrower {

    override val gameRenderPipeline: GameRenderPipeline = GameRenderPipeline(screen)

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
        get() = _encounterModifiers.any { it.second.disableEverlasting() }

    override val cardsInHand: List<Card>
        get() = cardHand.allCards()

    private val _encounterModifiers: MutableList<Pair<((GameController) -> Boolean)?, EncounterModifier>> = mutableListOf()

    override val encounterModifiers: List<EncounterModifier>
        get() = _encounterModifiers.map { it.second }

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

    @Inject(name = "shoot_button")
    override lateinit var shootButton: Actor

    @Inject
    override lateinit var revolver: Revolver

    @Inject
    private lateinit var cardHand: CardHand

    override lateinit var encounterContext: EncounterContext
        private set

    private lateinit var encounter: Encounter

    private var cardPrototypes: List<CardPrototype> = listOf()

    override val cardStack: CardStack = CardStack(mutableListOf())

    private val createdCards: MutableList<Card> = mutableListOf()

    override val allCards: List<Card>
        get() = createdCards

    private lateinit var defaultBullet: CardPrototype

    private val mainTimeline: Timeline = Timeline().also { it.startTimeline() }
    private val animTimelines: MutableList<Timeline> = mutableListOf()

    var cardsDrawn: Int = 0
        private set

    override val hasWon: Boolean
        get() = allEnemies.all { it.isDefeated }

    private val enemyBannerPromise: Promise<Drawable> =
        FortyFive.resourceManager.request(this, this.screen.lifetime, "enemy_turn_banner")

    private val playerBannerPromise: Promise<Drawable> =
        FortyFive.resourceManager.request(this, this.screen.lifetime, "player_turn_banner")

    private val softMaxCardsWarning = warningParent.Warning(
        "Maximum Card Number Reached\nAfter this turn, put all but ${Config.softMaxCards} cards at the bottom of your deck.",
        WarningParent.Level.MID
    )

    private val hardMaxCardsWarning = warningParent.Warning(
        "Hard Maximum Card Number Reached\nYou cant draw any more cards this turn. After this turn, put all but ${Config.softMaxCards} cards at the bottom of your deck.",
        WarningParent.Level.HIGH
    )

    private val enemyDifficulty
        get() = 1f + ((encounter.minorDifficulty - 1f) * RunGeneratorConfig.enemyDamageAdjustment)

    private lateinit var profile: Profile

    override fun init(context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterContext = context

        profile = FortyFive.profileManager.currentProfile!!

        FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.BATTLE)

        encounter = encounterContext.encounter
        encounter.encounterModifier.forEach {
            addEncounterModifier(it)
        }

        bindGameEventListeners()

        allEnemies = encounter.createEnemies()
        gameEvents.fire(Events.SetupEnemies(allEnemies))
        gameEvents.fire(Events.EnemySelected(allEnemies.first()))

        initCards()
        updateReserves(Config.baseReserves)
        appendMainTimeline(Timeline.timeline {
            delay(300)
            updateReserves(Config.baseReserves)
            action {
                _encounterModifiers.forEach { it.second.onStart(this@GameControllerImpl) }
            }
            action { chooseEnemyActions() }
            includeLater({ drawCardsTimeline(Config.cardsToDrawInFirstRound) })
            later {
                val startTriggerInformation = createTriggerInfo(null)
                val startEvent = Events.TurnBeginEvent(startTriggerInformation)
                gameEvents.fire(startEvent)
                include(startEvent.createTimeline())
            }
        })
    }

    override fun onShow() {
        FortyFive.useRenderPipeline(gameRenderPipeline)
    }

    private fun bindGameEventListeners() {
        gameEvents.watchFor<Events.ParryStateChange> { (inParryMenu) ->
            if (inParryMenu) gameRenderPipeline.startParryEffect() else gameRenderPipeline.stopParryEffect()
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
                    _encounterModifiers.forEach { (_, modifier) ->
                        val timeline = modifier.executeAfterBulletWasPlacedInRevolver(event.card, this@GameControllerImpl)
                        if (timeline != null) include(timeline)
                    }
                }
            }
            val situation = GameSituation.ZoneChange(event.card, event.oldZone, event.newZone, event.before)
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
        gameEvents.watchFor<Events.EndTurnEvent> { event ->
            val situation = GameSituation.TurnEnd
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
                encounterModifiers
                    .mapNotNull { it.executeOnEndTurn() }
                    .collectTimeline()
                    .let { include(it) }
            }
        }
        gameEvents.watchFor<Events.TurnBeginEvent> { event ->
            val situation = GameSituation.TurnBegin
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
                encounterModifiers
                    .mapNotNull { it.executeOnPlayerTurnStart(this@GameControllerImpl) }
                    .collectTimeline()
                    .let { include(it) }
                activeEnemies
                    .map { it.executeStatusEffectsAfterTurn() }
                    .collectTimeline()
                    .let { include(it) }
            }
        }
        gameEvents.watchFor<Events.RevolverRotatedEvent> { event ->
            val situation = GameSituation.RevolverRotation(event.rotation)
            event.append {
                _encounterModifiers.forEach { (_, modifier) ->
                    val timeline = modifier.executeAfterRevolverRotated(event.rotation, this@GameControllerImpl)
                    if (timeline != null) include(timeline)
                }
                include(checkTrigger(situation, event.triggerInformation))
                activeEnemies
                    .map { it.executeStatusEffectsAfterRevolverRotation(event.rotation) }
                    .collectTimeline()
                    .let { include(it) }
            }
        }
        gameEvents.watchFor<Events.CardReturnedHome> { event ->
            val situation = GameSituation.CardReturnedHome(event.card)
            event.append {
                include(checkTrigger(situation, event.triggerInformation))
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
            event.append {
                _encounterModifiers.forEach { (_, modifier) ->
                    val timeline = modifier.executeAfterRevolverWasShot(event.card, this@GameControllerImpl)
                    if (timeline != null) include(timeline)
                }
            }
        }
        gameEvents.watchFor<Events.CardRightClickEvent> { (card) ->
            if (card.rightClickCost == null) return@watchFor
            if (isUIFrozen) return@watchFor
            val triggerInformation = createTriggerInfo(card)
            val situation = GameSituation.CardRightClicked(card)
            appendMainTimeline(Timeline.timeline { later {
                val anyEffectTriggers = card.effects.any {
                    it.checkTrigger(situation, triggerInformation, this@GameControllerImpl, card)
                }
                if (anyEffectTriggers && tryPay(card.rightClickCost, card.actor)) {
                    include(checkTrigger(situation, triggerInformation))
                }
            } })
        }
    }

    private fun checkTrigger(situation: GameSituation, triggerInformation: TriggerInformation): Timeline = createdCards
        .map { it.checkEffects(situation, triggerInformation, this) }
        .collectTimeline()

    private fun updateReserves(newReserves: Int, sourceActor: Actor? = null) {
        if (curReserves == newReserves) return
        val prevReserves = curReserves
        curReserves = newReserves
        gameEvents.fire(Events.ReservesChanged(prevReserves, newReserves, sourceActor, this))
    }

    override fun update() {
        _encounterModifiers.removeIf { (predicate, _) -> predicate != null && !predicate(this@GameControllerImpl) }
        _encounterModifiers.forEach { it.second.update(this@GameControllerImpl) }

        animTimelines.forEach(Timeline::updateTimeline)
        mainTimeline.updateTimeline()
        createdCards.forEach { it.update(this) }
        updateStatusEffects()
        allEnemies.forEach { it.update() }
    }

    private fun initCards() {
        val onj = ConfigFileManager.getConfigFile("cards")

        val cards = encounter.forceCards
            ?: encounterContext.forceCards
            ?: profile.currentRunDeck!!.cards

        val cardsArray = onj.get<OnjArray>("cards")

        val stack = mutableListOf<Card>()

        cardPrototypes = Card
            .getFrom(cardsArray) { card ->
                createdCards.add(card)
                encounterModifiers.forEach { it.initBullet(card) }
                screen.lifetime.tieDisposable(card)
                card.setGame(this@GameControllerImpl)
            }
            .toMutableList()

        cards.forEach { cardName ->
            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            stack.add(card.create(this.screen))
        }

        if (encounter.shuffleCards) stack.shuffle()
        cardStack.set(stack)

        FortyFive.logger.debug(logTag, "card stack: $stack")

        val defaultBulletName = onj.get<String>("defaultBullet")

        defaultBullet = cardPrototypes
            .firstOrNull { it.name == defaultBulletName }
            ?: throw RuntimeException("unknown default bullet: $defaultBulletName")
    }

    override fun cardSelectionPopupTimeline(
        text: String,
        exclude: Card?
    ): Timeline = Timeline.timeline {
        val event = Events.TargetSelectionEvent(text, exclude)
        include(afterlife.closeTimeline())
        action { gameEvents.fire(event) }
        delayUntil { event.promise.isResolved }
        action { store("selectedCard", event.promise.getOrError()) }
    }

    override fun destroyCardTimeline(card: Card, sourceCard: Card?): Timeline = Timeline.timeline { later {
        if (!card.inZone(Zone.REVOLVER)) return@later
        val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.AFTERLIFE, before = true, triggerInfo)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        include(card.actor.destroyAnimation())
        action { card.actor.alpha = 0f }
        if (afterlife.isClosed) include(afterlife.openTimeline())
        action {
            revolver.removeCard(card)
            afterlife.pushCard(card)
            card.actor.alpha = 1f
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
    } }

    override fun putCardsInStackTimeline(
        cardName: String,
        amount: Int,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        val proto = cardPrototypes.find { it.name == cardName }
        requireNotNull(proto) { "No card with name $cardName" }
        repeat(amount) {
            val card = proto.create(screen)
            val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
            val beforeEvent = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.STACK, before = true, triggerInfo)
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action { cardStack.addCardAtTop(card) }
            later {
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    } }

    override fun tryToPutCardsInHandTimeline(
        cardName: String,
        amount: Int,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        val prototype = cardPrototypes.find { it.name == cardName } ?: throw RuntimeException("unknown card $cardName")
        val newAmount = maxSpaceInHand(amount)
        if (newAmount == 0) return@later
        repeat(newAmount) {
            val card = prototype.create(screen)
            val triggerInfo = createTriggerInfo(card, sourceCard = sourceCard)
            val beforeEvent = Events.CardChangeZoneEvent(card, Zone.LIMBO, Zone.HAND, before = true, triggerInfo)
            includeLater({
                gameEvents.fire(beforeEvent)
                beforeEvent.createTimeline()
            })
            action { cardHand.addCard(card) }
            include(card.actor.spawnAnimation())
            action { checkCardMaximums() }
            later {
                val afterEvent = beforeEvent.copy(before = false)
                gameEvents.fire(afterEvent)
                include(afterEvent.createTimeline())
            }
        }
    } }

    override fun bounceBulletTimeline(card: Card): Timeline = Timeline.timeline {
        val info = createTriggerInfo(card)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.HAND, before = true, info)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        action {
            if (card !in revolver.slots.mapNotNull { it.card }) {
                throw RuntimeException("cant bounce card $card because it isn't in the revolver")
            }
            revolver.removeCard(card)
            tryPutCardInHand(card)
        }
        includeLater({
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            afterEvent.createTimeline()
        })
    }

    override fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean,
        sourceCard: Card?
    ): Timeline = Timeline.timeline { later {
        var newRotation = if (ignoreEncounterModifiers) {
            rotation
        } else {
            encounterModifiers.fold(rotation) { acc, cur -> cur.modifyRevolverRotation(acc) }
        }
        playerStatusEffects.forEach { newRotation = it.modifyRevolverRotation(newRotation) }
        include(revolver.rotate(newRotation))
        action {
            revolverRotationCounter += newRotation.amount
            cardsInRevolver().forEach { it.onRevolverRotation(newRotation)  }
        }
        if (newRotation.amount == 0) return@later
        later {
            revolver
                .slots
                .filter { it.card?.enteredInSlot == it.num }
                .map {
                    val card = it.card!!
                    val info = createTriggerInfo(card, sourceCard = sourceCard)
                    val event = Events.CardReturnedHome(card, info)
                    gameEvents.fire(event)
                    event.createTimeline()
                }
                .collectTimeline()
                .let { include(it) }
        }
        later {
            val info = createTriggerInfo(null, multiplier = newRotation.amount, sourceCard = sourceCard)
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

        val cardAcc = mutableListOf<Card>()
        repeat(cardsToDraw) {
            include(drawCardTimeline(fromBottom, sourceCard, cardAcc))
        }

        skipping { skip ->
            action { if (cardsToDraw <= 0) skip() }
            later {
                val info = createTriggerInfo(
                    null,
                    amountOfCardsDrawn = cardsToDraw,
                    multiplier = cardsToDraw,
                    sourceCard = sourceCard
                )
                val event = Events.CardsDrawnEvent(cardsToDraw, isSpecial, fromBottom, cardAcc, info)
                gameEvents.fire(event)
                include(event.createTimeline())
            }
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
            val card = card
            if (card == null) {
                include(putCardFromStackInHandTimeline(defaultBullet.create(screen), sourceCard, cardIsntActuallyInStack = true))
            } else {
                include(putCardFromStackInHandTimeline(card, sourceCard))
            }
        }
    }

    override fun putCardFromStackInHandTimeline(
        card: Card,
        source: Card?,
        cardIsntActuallyInStack: Boolean, // kinda stupid, but necessary when drawing the default bullet
    ): Timeline = Timeline.timeline {
        var orbAnimationTimeline: Timeline? = null
        val info = createTriggerInfo(card, sourceCard = source)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.STACK, Zone.HAND, before = true, info)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        action {
            if (!cardIsntActuallyInStack) cardStack.remove(card)
            cardHand.addCard(card)
            card.actor.alpha = 0f
            val event = Events.PlayCardOrbAnimation(card.actor)
            gameEvents.fire(event)
            orbAnimationTimeline = event.orbAnimationTimeline
        }
        includeLater({ Timeline.timeline {
            include(orbAnimationTimeline!!)
            action { card.actor.alpha = 1f }
            include(card.actor.spawnAnimation())
        } }, { orbAnimationTimeline != null })
        includeLater({
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            afterEvent.createTimeline()
        })
        action { checkCardMaximums() }
    }

    private fun maxSpaceInHand(desiredSpace: Int = Int.MAX_VALUE): Int =
        (Config.hardMaxCards - cardHand.amountOfCards).between(0, desiredSpace)

    override fun tryApplyStatusEffectToEnemyTimeline(
        statusEffect: StatusEffect,
        enemy: Enemy
    ): Timeline = Timeline.timeline { later {
        if (encounterModifiers.any { !it.shouldApplyStatusEffects() }) return@later
        action { enemy.applyEffect(statusEffect, this@GameControllerImpl) }
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
        if (newDamage != damage) include(shieldAnimationTimeline())
        if (newDamage == 0) return@later
        include(updatePlayerLivesTimeline(curPlayerLives - newDamage))
        action {
            FortyFive.soundPlayer.situation("enemy_attack", this@GameControllerImpl.screen)
            dispatchAnimTimeline(gameRenderPipeline.getScreenShakeTimeline())
            dispatchAnimTimeline(GraphicsConfig.damageOverlay(screen, this@GameControllerImpl).wrap())
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
            val info = createTriggerInfo(null)
            val event = Events.PlayerLivesChanged(old, newValue, info)
            gameEvents.fire(event)
            event.createTimeline()
        })
    } }

    private val shieldIconPromise: Promise<Drawable> =
        FortyFive.resourceManager.request(this, this.screen.lifetime, "shield_icon_large")

    private val shieldShaderPromise: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, this.screen.lifetime, "glow_shader_shield")

    private fun shieldAnimationTimeline(): Timeline {
        val shieldIcon = shieldIconPromise.getOrNull() ?: return Timeline()
        val shieldShader = shieldShaderPromise.getOrNull() ?: return Timeline()
        return Timeline.timeline {
            val bannerAnim = BannerAnimation(
                shieldIcon,
                screen,
                1_000,
                150,
                0.3f,
                0.5f,
                interpolation = Interpolation.pow2In,
                customShader = shieldShader
            ).asTimeline(this@GameControllerImpl).asAction()
            val postProcessorAction = Timeline.timeline {
                delay(100)
                include(gameRenderPipeline.getScreenShakePopoutTimeline())
                delay(50)
                action { FortyFive.soundPlayer.situation("shield_anim", screen) }
            }.asAction()
            parallelActions(bannerAnim, postProcessorAction)
        }
    }

    override fun playerDeathTimeline(): Timeline = Timeline.timeline {
        action {
            FortyFive.logger.debug(logTag, "player lost")
            animTimelines.forEach(Timeline::stopTimeline)
        }
        include(gameRenderPipeline.getFadeToBlackTimeline(2000, stayBlack = true))
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

    override fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect): Timeline = Timeline.timeline {
        action {
            FortyFive.logger.debug(logTag, "status effect $effect applied to player")
            _playerStatusEffects
                .find { it.canStackWith(effect) }
                ?.let {
                    FortyFive.logger.debug(logTag, "stacked with $it")
                    it.stack(effect)
                    return@action
                }
            effect.start(this@GameControllerImpl)
            _playerStatusEffects.add(effect)
            gameEvents.fire(Events.AddedPlayerStatusEffect(effect))
        }
    }

    private fun updateStatusEffects() {
        _playerStatusEffects.iterateRemoving { effect, remover ->
            if (effect.isStillValid()) return@iterateRemoving
            remover()
            gameEvents.fire(Events.RemovedPlayerStatusEffect(effect))
        }
    }

    override fun destroyCardInHandTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    private fun parryTimeline(
        damage: Int,
        isPiercing: Boolean,
        card: Card
    ): Timeline = Timeline.timeline { later {
        FortyFive.soundPlayer.situation("enter_parry", this@GameControllerImpl.screen)
        val damageOfCard = card.curDamage(this@GameControllerImpl)
        val remainingDamage = if (card.isReinforced) 0 else (damage - damageOfCard).coerceAtLeast(0)
        val parryEnterEvent = Events.ParryStateChange(true, damage, damageOfCard)
        val parryLeaveEvent = Events.ParryStateChange(false, 0, 0)
        parryEnterEvent.resolutionPromise.then { gameEvents.fire(parryLeaveEvent) }
        include(afterlife.closeTimeline())
        action { gameEvents.fire(parryEnterEvent) }
        delayUntil { parryEnterEvent.resolutionPromise.isResolved }
        later {
            val parried = parryEnterEvent.resolutionPromise.getOrError()
            if (parried) {
                include(card.afterShot(this@GameControllerImpl, ::putCardBackInHandAfterShot, ::putCardInTheStackAfterShot))
                include(rotateRevolverTimeline(card.rotationDirection))
                if (remainingDamage > 0) {
                    include(damagePlayerTimeline(remainingDamage, false, isPiercing))
                }
            } else {
                include(damagePlayerTimeline(damage, false, isPiercing))
            }
        }
    } }

    override fun enemyAttackTimeline(
        damage: Int,
        isPiercing: Boolean
    ): Timeline = Timeline.timeline { later {
        val card = revolver.getCardInSlot(5)
        if (card == null) {
            include(damagePlayerTimeline(damage, false, isPiercing))
        } else {
            include(parryTimeline(damage, isPiercing, card))
        }
    } }

    override fun putBulletFromRevolverUnderTheDeckTimeline(card: Card): Timeline {
        TODO("Not yet implemented")
    }

    private fun putCardBackInHandAfterShot(card: Card): Timeline = Timeline.timeline {
        val triggerInformation = createTriggerInfo(card)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.HAND, before = true, triggerInformation)
        includeLater({
            gameEvents.fire(beforeEvent)
            beforeEvent.createTimeline()
        })
        action {
            revolver.removeCard(card)
            tryPutCardInHand(card)
        }
        later {
            val afterEvent = beforeEvent.copy(before = false)
            gameEvents.fire(afterEvent)
            include(afterEvent.createTimeline())
        }
    }

    private fun tryPutCardInHand(card: Card): Boolean {
        val spaceInHand = maxSpaceInHand(1)
        if (spaceInHand == 0) return false
        cardHand.addCard(card)
        checkCardMaximums()
        return true
    }

    private fun putCardInTheStackAfterShot(card: Card): Timeline = Timeline.timeline {
        val triggerInformation = createTriggerInfo(card)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.REVOLVER, Zone.STACK, before = true, triggerInformation)
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

        if (encounterModifiers.any { !it.canShootRevolver(this@GameControllerImpl) }) return@later
        val cardToShoot = revolver.getCardInSlot(5)
        val rotationDirection = cardToShoot?.rotationDirection ?: RevolverRotation.Right(1)

        FortyFive.logger.debug(logTag,
            "revolver is shooting;" +
                    "cardToShoot = $cardToShoot"
        )

        if (cardToShoot?.canBeShot(this@GameControllerImpl)?.not() ?: false) {
            FortyFive.logger.debug(logTag, "Card can't be shot because it blocks")
            return@later
        }

        val targetedEnemies = if (cardToShoot?.isSpray ?: false) allEnemies else listOf(targetedEnemy())

        val triggerInfo = TriggerInformation(
            controller = this@GameControllerImpl,
            targetedEnemies = targetedEnemies,
            sourceCard = cardToShoot,
            isOnShot = true,
        )

        action {
            FortyFive.soundPlayer.situation("revolver_shot", screen)
            val postProcessor = gameRenderPipeline.getOnShotPostProcessingTimeline()
            dispatchAnimTimeline(postProcessor)
        }
        cardToShoot?.let { card ->
            targetedEnemies
                .map { it.damage(cardToShoot.curDamage(this@GameControllerImpl)) }
                .collectTimeline()
                .let { include(it) }
            // Not handled via event because things like encounter modifiers or
            // status effects shouldn't hook into here
            include(checkTrigger(GameSituation.OnShot(card), triggerInfo))
            include(card.afterShot(this@GameControllerImpl, ::putCardBackInHandAfterShot, ::putCardInTheStackAfterShot))
        }
        include(rotateRevolverTimeline(rotationDirection))
        includeLater(
            { damagePlayerTimeline(Config.shotEmptyDamage) },
            { cardToShoot == null }
        )

        if (cardToShoot != null) later {
            val event = Events.AfterShotEvent(cardToShoot, triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    } }

    override fun shoot() {
        val postProcessor = gameRenderPipeline.getOnShotPostProcessingTimeline().asAction()
        appendMainTimeline(Timeline.timeline {
            parallelActions(shootTimeline().asAction(), postProcessor)
        })
//        appendMainTimeline(shootTimeline())
    }

    override fun gainReserves(amount: Int, source: Actor?) {
        updateReserves(curReserves + amount, source)
    }

    override fun tryPay(cost: Int, animTarget: Actor?): Boolean {
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

    override fun addTemporaryEncounterModifier(
        modifier: EncounterModifier,
        validityChecker: (GameController) -> Boolean
    ) {
        _encounterModifiers.add(validityChecker to modifier)
        // No event in this case, because temporary encounter modifiers aren't displayed
    }

    override fun addEncounterModifier(modifier: EncounterModifier) {
        _encounterModifiers.add(null to modifier)
        gameEvents.fire(Events.EncounterModifierAdded(modifier))
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
        var cardInSlot: Card? = null
        val info = createTriggerInfo(card, sourceCard = card)
        val beforeEvent = Events.CardChangeZoneEvent(card, Zone.HAND, Zone.REVOLVER, before = true, info)
        val timeline = Timeline.timeline {
            skipping { skip ->
                action {
                    FortyFive.logger.debug(logTag, "attempting to load bullet $card in revolver slot $slot")
                    cardInSlot = revolver.getCardInSlot(slot)
                    val blockedByCard = cardInSlot != null && !cardInSlot!!.canBeReplaced(this@GameControllerImpl, card)
                    val shouldSkip = !card.allowsEnteringGame(this@GameControllerImpl, slot)
                        || blockedByCard
                        || !tryPay(card.baseCost, card.actor)
                    if (!shouldSkip) return@action
                    FortyFive.soundPlayer.situation("not_allowed", screen)
                    skip()
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
                includeLater(
                    { cardInSlot!!.replaceTimeline(this@GameControllerImpl, card) },
                    { cardInSlot != null }
                )
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
        appendMainTimeline(timeline)
    }

    private fun bannerAnimationTimeline(isPlayer: Boolean): Timeline =
        (if (isPlayer) playerBannerPromise else enemyBannerPromise).getOrNull()?.let { banner ->
            BannerAnimation(
                banner,
                this.screen,
                1_500,
                500,
                1.4f,
                1.1f
            ).asTimeline(this)
        } ?: Timeline()

    private fun enemyActionTimeline(): Timeline = Timeline.timeline {
        activeEnemies.forEach { enemy -> later {
            val action = enemy.resolveAction(this@GameControllerImpl, 1.0) ?: return@later
            if (action.prototype.hasSpecialAnimation) {
                val event = Events.PlayEnemySpecialAttackAnim(this@GameControllerImpl, action)
                gameEvents.fire(event)
                delay(300)
                include(event.createTimeline())
                delayUntil { event.finishedPromise.isResolved }
                delay(100)
                val data = EnemyAction.ExecutionData(newDamage = action.directDamageDealt + enemy.additionalDamage)
                include(action.getTimeline(data))
                delay(400)
            } else {
                val event = Enemy.PlayChargeAnimationEvent()
                enemy.enemyEvents.fire(event)
                action {
                    event.timeline.getOrNull()?.let { dispatchAnimTimeline(it) }
                }
                delay(200)
                val data = EnemyAction.ExecutionData(newDamage = action.directDamageDealt + enemy.additionalDamage)
                include(action.getTimeline(data))
                delay(400)
            }
            action {
                val event = Enemy.EnemyActionChangedEvent(NextEnemyAction.None, 0, null)
                enemy.enemyEvents.fire(event)
            }
        } }
    }

    private fun winTimeline(): Timeline = Timeline.timeline { later {
        val money = -allEnemies.sumOf { it.currentHealth }
        val playerGetsCard = !encounter.special &&
                !encounterContext.isExtraction &&
                Utils.coinFlip(Config.playerGetsRewardCardChance)
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
                FortyFive.soundPlayer.situation("money_earned", this@GameControllerImpl.screen)
                profile.earnMoney(money)
            }
        }
        delay(300)
        action {
            encounterContext.completed()
            profile.write()
            profile.writeMaps()

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
        include(enemyActionTimeline())
        include(bannerAnimationTimeline(true))

        action {
            turnCounter++
        }

        action {
            chooseEnemyActions()
            FortyFive.soundPlayer.situation("turn_begin", screen)
            updateReserves(Config.baseReserves, revolver)
        }

        includeLater({ drawCardsTimeline(Config.cardsToDraw) })

        later {
            val triggerInfo = createTriggerInfo(null)
            val event = Events.TurnBeginEvent(triggerInfo)
            gameEvents.fire(event)
            include(event.createTimeline())
        }
    } }

    private fun endTurn() {
        appendMainTimeline(endTurnTimeline())
    }

    private fun chooseEnemyActions() {
        val otherActions = mutableListOf<NextEnemyAction>()
        activeEnemies.forEach { enemy ->
            val action = enemy.chooseNewAction(this, enemyDifficulty.toDouble(), otherActions)
            otherActions.add(action)
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

    override fun titleOfCard(cardName: String): String = cardPrototypes.find { it.name == cardName }?.title
        ?: throw RuntimeException("No card with name $cardName")

    override fun end() {
        super.end()
    }

    private fun createTriggerInfo(
        card: Card?,
        multiplier: Int? = null,
        isOnShot: Boolean = false,
        amountOfCardsDrawn: Int = 0,
        sourceCard: Card? = null,
    ): TriggerInformation = TriggerInformation(
        controller = this,
        targetedEnemies = if (card?.isSpray == true) allEnemies else listOf(targetedEnemy),
        multiplier = multiplier,
        isOnShot = isOnShot,
        amountOfCardsDrawn = amountOfCardsDrawn,
        sourceCard = sourceCard
    )

    companion object {
        const val logTag = "GameController"
    }

    enum class Zone {
        STACK, HAND, REVOLVER, AFTERLIFE, LIMBO
    }

    object Config {
        const val baseReserves = 4
        const val softMaxCards = 12
        const val hardMaxCards = 20
//        const val cardsToDrawInFirstRound = 20
        const val cardsToDrawInFirstRound = 6
//        const val cardsToDraw = 5
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
            val sourceActor: Actor? = null,
            val controller: GameController
        )
        data class PlayCardOrbAnimation(val targetActor: Actor, var orbAnimationTimeline: Timeline? = null)
        data class ParryStateChange(
            val inParryMenu: Boolean,
            val damage: Int,
            val ableToBlock: Int,
            val resolutionPromise: Promise<Boolean /*= parried*/> = Promise()
        )
        data class TargetSelectionEvent(val text: String, val exclude: Card?, val promise: Promise<Card> = Promise())
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

        data class CardReturnedHome(
            val card: Card,
            val triggerInformation: TriggerInformation
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

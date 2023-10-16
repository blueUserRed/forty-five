package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.EncounterMapEvent
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.screen.gameComponents.*
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.max
import java.lang.Integer.min

/**
 * the Controller for the main game screen
 */
class GameController(onj: OnjNamedObject) : ScreenController() {

    val gameDirector = GameDirector(this)

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardDragBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
    private val cardDropBehaviour = onj.get<OnjNamedObject>("cardDropBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private val enemyAreaOnj = onj.get<OnjObject>("enemyArea")
    private val cardSelectorOnj = onj.get<OnjObject>("cardSelector")
    private val warningParentName = onj.get<String>("warningParentName")
    private val statusEffectDisplayName = onj.get<String>("statusEffectDisplayName")
    private val putCardsUnderDeckWidgetOnj = onj.get<OnjObject>("putCardsUnderDeckWidget")

    val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()

    val baseReserves by templateParam(
        "game.baseReserves", onj.get<Long>("reservesAtRoundBegin").toInt()
    )

    val softMaxCards = onj.get<Long>("softMaxCards").toInt()
    val hardMaxCards = onj.get<Long>("hardMaxCards").toInt()

    private val shotEmptyDamage = onj.get<Long>("shotEmptyDamage").toInt()

    /**
     * stores the screenDataProvider for the game-screen
     */
    lateinit var curScreen: OnjScreen
        private set

    lateinit var cardHand: CardHand
        private set
    lateinit var revolver: Revolver
        private set
    lateinit var enemyArea: EnemyArea
        private set
    lateinit var cardSelector: CircularCardSelector
        private set
    lateinit var warningParent: CustomWarningParent
        private set
    lateinit var putCardsUnderDeckWidget: PutCardsUnderDeckWidget
        private set
    lateinit var statusEffectDisplay: StatusEffectDisplay
        private set

    private var cardPrototypes: List<CardPrototype> = listOf()
    val createdCards: MutableList<Card> = mutableListOf()
    private var cardStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var _remainingCards: Int by multipleTemplateParam(
        "game.cardsInStack", 0,
        "game.cardsInStackPluralS" to { if (it == 1) "" else "s" }
    )

    // currently not used, but might be useful for an encouter modifier later
    var remainingTurns: Int by multipleTemplateParam(
        "game.remainingTurnsRaw", -1,
        "game.remainingTurns" to { if (it == -1) "?" else it.toString() }
    )

    var curPlayerLives: Int
        set(value) {
            val newLives = max(value, 0)
            SaveState.playerLives = newLives
        }
        get() = SaveState.playerLives

    @Suppress("unused")
    val playerLivesAtStart: Int by templateParam("game.basePlayerLives", SaveState.playerLives)

    private var popupText: String by templateParam("game.popupText", "")
    private var popupButtonText: String by templateParam("game.popupButtonText", "")
    private var popupEvent: Event? = null

    private val mainTimeline: Timeline = Timeline(mutableListOf()).apply { startTimeline() }

    private val animTimelines: MutableList<Timeline> = mutableListOf()
    private val animTimelinesAddBuffer: MutableList<Timeline> = mutableListOf()

    private var isUIFrozen: Boolean = false

    /**
     * counts up every turn; starts at 0, but gets immediately incremented to one
     */
    var turnCounter: Int = 0
        private set(value) {
            field = value
            FortyFiveLogger.title("round: $value")
        }

    /**
     * counts up every revolver turn; starts at 0
     */
    var revolverRotationCounter: Int = 0
        private set

    var curReserves: Int by templateParam("game.curReserves", 0)

    private var curGameAnims: MutableList<GameAnimation> = mutableListOf()

    private lateinit var defaultBullet: CardPrototype

    private lateinit var gameRenderPipeline: GameRenderPipeline
    private lateinit var encounterMapEvent: EncounterMapEvent

    private val encounterModifier: MutableList<EncounterModifier> = mutableListOf()

    var reservesSpent: Int = 0
        private set

    var cardsDrawn: Int = 0
        private set

    private var hasWon: Boolean = false

    var playerLost: Boolean = false
        private set

    private val _playerStatusEffects: MutableList<StatusEffect> = mutableListOf()

    val playerStatusEffects: List<StatusEffect>
        get() = _playerStatusEffects

    private var permanentWarningId: Int? = null
    private var isPermanentWarningHard: Boolean = false

    @MainThreadOnly
    override fun init(onjScreen: OnjScreen, context: Any?) {
        if (context !is EncounterMapEvent) { // TODO: comment back in
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterMapEvent = context
        curScreen = onjScreen
        FortyFive.currentGame = this
        gameRenderPipeline = GameRenderPipeline(onjScreen)
        FortyFive.useRenderPipeline(gameRenderPipeline)

        FortyFiveLogger.title("game starting")

        warningParent = onjScreen.namedActorOrError(warningParentName) as? CustomWarningParent
            ?: throw RuntimeException("actor named $warningParentName must be of type CustomWarningParent")
        statusEffectDisplay = onjScreen.namedActorOrError(statusEffectDisplayName) as? StatusEffectDisplay
            ?: throw RuntimeException("actor named $statusEffectDisplayName must be of type StatusEffectDisplay")
        initCards()
        initCardHand()
        initRevolver()
        initCardSelector()
        initPutCardsUnderDeckWidget()
        // enemy area is initialised by the GameDirector
        gameDirector.init()
        curReserves = baseReserves
        appendMainTimeline(drawCardPopupTimeline(cardsToDrawInFirstRound))
        onjScreen.invalidateEverything()
    }

    private fun initCards() {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cardsArray = onj.get<OnjArray>("cards")
        cardsArray.value.forEach { card ->
            card as OnjObject
            curScreen.borrowResource("${Card.cardTexturePrefix}${card.get<String>("name")}")
            card
                .get<OnjArray>("forceLoadCards")
                .value
                .forEach { curScreen.borrowResource("${Card.cardTexturePrefix}${it.value as String}") }
        }
        onj
            .get<OnjArray>("alwaysLoadCards")
            .value
            .forEach { curScreen.borrowResource("${Card.cardTexturePrefix}${it.value as String}") }

        cardPrototypes = Card
            .getFrom(cardsArray, curScreen, ::initCard)
            .toMutableList()

        SaveState.cards.forEach { cardName ->
            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            cardStack.add(card.create())
        }

        cardStack.shuffle()

        _remainingCards = cardStack.size

        FortyFiveLogger.debug(logTag, "card stack: $cardStack")

        val defaultBulletName = onj.get<String>("defaultBullet")

        defaultBullet = cardPrototypes
            .filter { it.type == Card.Type.BULLET }
            .firstOrNull { it.name == defaultBulletName }
            ?: throw RuntimeException("unknown default bullet: $defaultBulletName")

    }

    private fun initCard(card: Card) {
        val dragBehaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
            cardDragBehaviour.name,
            cardDragAndDrop,
            card.actor,
            cardDragBehaviour
        )
        val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
            cardDropBehaviour.name,
            cardDragAndDrop,
            card.actor,
            cardDropBehaviour
        )
        cardDragAndDrop.addSource(dragBehaviour)
        cardDragAndDrop.addTarget(dropBehaviour)
        createdCards.add(card)
    }

    override fun onUnhandledEvent(event: Event) = when (event) {
        is ShootRevolverEvent -> {
            shoot()
        }
        is EndTurnEvent -> {
            endTurn()
        }
        is PopupConfirmationEvent, is PopupSelectionEvent, is DrawCardEvent, is ParryEvent -> {
            popupEvent = event
        }
        else -> { }
    }

    fun nextTurn() {
        turnCounter++
        if (remainingTurns != -1) {
            FortyFiveLogger.debug(logTag, "$remainingTurns turns remaining")
            remainingTurns--
        }
        gameDirector.onNewTurn()
    }

    private var updateCount = 0 // TODO: this is stupid

    @MainThreadOnly
    override fun update() {
        if (updateCount < 6) curScreen.invalidateEverything() // TODO: this is stupid
        updateCount++

        if (mainTimeline.isFinished && isUIFrozen) unfreezeUI()
        if (!mainTimeline.isFinished && !isUIFrozen) freezeUI()

        mainTimeline.updateTimeline()

        animTimelinesAddBuffer.forEach { animTimelines.add(it) }
        animTimelinesAddBuffer.clear()
        val iterator = animTimelines.iterator()
        while (iterator.hasNext()) {
            val cur = iterator.next()
            cur.updateTimeline()
            if (!cur.isFinished && !cur.hasBeenStarted) cur.startTimeline()
            if (cur.isFinished) iterator.remove()
        }

        updateStatusEffects()
        updateGameAnimations()
    }

    private fun updateGameAnimations() {
        val iterator = curGameAnims.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            if (anim.isFinished()) {
                anim.end()
                iterator.remove()
            }
            anim.update()
        }
    }

    /**
     * plays a gameAnimation
     */
    @AllThreadsAllowed
    fun playGameAnimation(anim: GameAnimation) {
        FortyFiveLogger.debug(logTag, "playing game animation: $anim")
        anim.start()
        curGameAnims.add(anim)
    }

    private fun initCardHand() {
        val curScreen = curScreen
        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActorOrError(cardHandName)
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        cardHand.init(this)
        this.cardHand = cardHand
    }

    private fun initRevolver() {
        val revolverName = revolverOnj.get<String>("actorName")
        val revolver = curScreen.namedActorOrError(revolverName)
        if (revolver !is Revolver) throw RuntimeException("actor named $revolverName must be a Revolver")
        val dropOnj = revolverOnj.get<OnjNamedObject>("dropBehaviour")
        revolver.initDragAndDrop(cardDragAndDrop to dropOnj)
        this.revolver = revolver
    }

    private fun initPutCardsUnderDeckWidget() {
        val name = putCardsUnderDeckWidgetOnj.get<String>("actorName")
        val actor = curScreen.namedActorOrError(name) as? PutCardsUnderDeckWidget
            ?: throw RuntimeException("actor named $name must be of type PutCardsUnderDeckWidget")
        val dropOnj = putCardsUnderDeckWidgetOnj.get<OnjNamedObject>("dropBehaviour")
        actor.initDragAndDrop(cardDragAndDrop, dropOnj)
        this.putCardsUnderDeckWidget = actor
    }

    private fun initCardSelector() {
        val curScreen = curScreen
        val cardSelectorName = cardSelectorOnj.get<String>("actorName")
        val cardSelector = curScreen.namedActorOrError(cardSelectorName)
        if (cardSelector !is CircularCardSelector) {
            throw RuntimeException("actor named $cardSelectorName must be a CircularCardSelector")
        }
        this.cardSelector = cardSelector
    }

    fun initEnemyArea(enemy: Enemy) {
        val curScreen = curScreen

        val enemyAreaName = enemyAreaOnj.get<String>("actorName")
        val enemyArea = curScreen.namedActorOrError(enemyAreaName)
        if (enemyArea !is EnemyArea) throw RuntimeException("actor named $enemyAreaName must be a EnemyArea")

        enemyArea.addEnemy(enemy)

//        if (enemyArea.enemies.isEmpty()) throw RuntimeException("enemyArea must have at least one enemy")
//        if (enemyArea.enemies.size != 1) enemyArea.selectedEnemy = enemyArea.enemies[0]

        this.enemyArea = enemyArea
    }

    /**
     * puts [card] in [slot] of the revolver (checks if the card is a bullet)
     */
    @MainThreadOnly
    fun loadBulletInRevolver(card: Card, slot: Int) = appendMainTimeline(Timeline.timeline {
        if (card.type != Card.Type.BULLET || !card.allowsEnteringGame(this@GameController)) return
        val cardInSlot = revolver.getCardInSlot(slot)
        if (!(cardInSlot?.isReplaceable ?: true)) return
        if (!cost(card.cost)) return
        action {
            cardHand.removeCard(card)
        }
        includeLater(
            { destroyCardTimeline(cardInSlot!!) },
            { cardInSlot != null }
        )
        action {
            revolver.setCard(slot, card)
            FortyFiveLogger.debug(logTag, "card $card entered revolver in slot $slot")
            card.onEnter()
            checkCardMaximums()
        }
        includeLater(
            { checkEffectsSingleCard(Trigger.ON_ENTER, card) },
            { true }
        )
    })

    fun putCardFromRevolverBackInHand(card: Card) {
        revolver.removeCard(card)
        card.leaveGame()
        cardHand.addCard(card)
        checkCardMaximums()
    }

    fun destroyCardInHand(card: Card) {
        cardHand.removeCard(card)
        checkCardMaximums()
    }

    private fun maxCardsPopupTimeline(): Timeline =
        confirmationPopupTimeline("Hand reached maximum of $hardMaxCards cards")

    fun confirmationPopupTimeline(text: String): Timeline = Timeline.timeline {
        action {
            curScreen.enterState(showPopupScreenState)
            curScreen.enterState(showPopupConfirmationButtonScreenState)
            popupText = text
            popupButtonText = "Ok"
        }
        delayUntil { popupEvent != null }
        action {
            popupEvent = null
            curScreen.leaveState(showPopupScreenState)
            curScreen.leaveState(showPopupConfirmationButtonScreenState)
        }
    }

    fun cardSelectionPopupTimeline(text: String, exclude: Card? = null): Timeline = Timeline.timeline {
        action {
            cardSelector.setTo(revolver, exclude)
            curScreen.enterState(showPopupScreenState)
            curScreen.enterState(showPopupCardSelectorScreenState)
            popupText = text
        }
        delayUntil { popupEvent != null }
        action {
            val event = popupEvent as PopupSelectionEvent
            store("selectedCard", revolver.slots[event.cardNum].card!!)
            popupEvent = null
            curScreen.leaveState(showPopupScreenState)
            curScreen.leaveState(showPopupCardSelectorScreenState)
        }
    }

    fun drawCardPopupTimeline(amount: Int): Timeline = Timeline.timeline {
        var remainingCardsToDraw = amount
        action {
            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(hardMaxCards - cardHand.cards.size)
            FortyFiveLogger.debug(logTag, "drawing cards in initial draw: $remainingCardsToDraw")
            if (remainingCardsToDraw != 0) curScreen.enterState(cardDrawActorScreenState)
            TemplateString.updateGlobalParam("game.remainingCardsToDraw", amount)
            TemplateString.updateGlobalParam(
                "game.remainingCardsToDrawPluralS",
                if (amount == 1) "" else "s"
            )
        }
        includeLater({ maxCardsPopupTimeline() }, { remainingCardsToDraw == 0 })
        includeLater({ Timeline.timeline {
            repeat(remainingCardsToDraw) { cur ->
                delayUntil { popupEvent != null }
                action {
                    popupEvent = null
                    drawCard()
                    TemplateString.updateGlobalParam("game.remainingCardsToDraw", amount - cur - 1)
                    TemplateString.updateGlobalParam(
                        "game.remainingCardsToDrawPluralS",
                        if (amount - cur - 1 == 1) "" else "s"
                    )
                }
            }
        }}, { remainingCardsToDraw != 0 })
        action {
            if (remainingCardsToDraw == 0) return@action
            curScreen.leaveState(cardDrawActorScreenState)
            checkCardMaximums()
        }
    }

    fun enemyAttackTimeline(damage: Int): Timeline = Timeline.timeline {
        var parryCard: Card? = null
        action {
            parryCard = revolver.slots[4].card
            curScreen.enterState(showEnemyAttackPopupScreenState)
        }
        delayUntil { popupEvent != null || parryCard == null }
        includeLater(
            { Timeline.timeline {
                val parryCard = parryCard!!
                val remainingDamage = damage - parryCard.curDamage
                action {
                    popupEvent = null
                    curScreen.leaveState(showEnemyAttackPopupScreenState)
                    revolver.removeCard(parryCard)
                    parryCard.leaveGame()

                }
                include(rotateRevolver(parryCard.rotationDirection))
                if (remainingDamage > 0) include(damagePlayerTimeline(remainingDamage))
            } },
            { popupEvent is ParryEvent && parryCard != null }
        )
        includeLater(
            { Timeline.timeline {
                action {
                    popupEvent = null
                    curScreen.leaveState(showEnemyAttackPopupScreenState)
                }
                include(damagePlayerTimeline(damage))
            } },
            { popupEvent is PopupConfirmationEvent || parryCard == null }
        )
    }

    /**
     * creates a new instance of the card named [name] and puts it in the hand of the player
     */
    @AllThreadsAllowed
    fun tryToPutCardsInHandTimeline(name: String, amount: Int = 1): Timeline = Timeline.timeline {
        var cardsToDraw = 0
        action {
            val maxCards = hardMaxCards - cardHand.cards.size
            cardsToDraw = min(maxCards, amount)
        }
        action {
            println("draw $cardsToDraw cards; card: $name")
            if (cardsToDraw == 0) return@action
            val cardProto = cardPrototypes
                .firstOrNull { it.name == name }
                ?: throw RuntimeException("unknown card: $name")
            repeat(cardsToDraw) {
                cardHand.addCard(cardProto.create())
            }
            FortyFiveLogger.debug(logTag, "card $name entered hand")
            checkCardMaximums()
        }
    }

    private fun checkCardMaximums() {
        val cards = cardHand.cards.size
        val permanentWarningId = permanentWarningId
        if (cards == hardMaxCards) {
            if (permanentWarningId != null) {
                if (isPermanentWarningHard) return
                warningParent.removePermanentWarning(permanentWarningId)
            }
            val id = warningParent.addPermanentWarning(
                curScreen,
                "Hard Maximum Card Number Reached",
                "You can't draw any more cards in this turn. After this turn, " +
                        "put all but $softMaxCards cards at the bottom of your deck.",
                CustomWarningParent.Severity.HIGH
            )
            this.permanentWarningId = id
            isPermanentWarningHard = true
        } else if (cards >= softMaxCards) {
            if (permanentWarningId != null) {
                if (!isPermanentWarningHard) return
                warningParent.removePermanentWarning(permanentWarningId)
            }
            val id = warningParent.addPermanentWarning(
                curScreen,
                "Maximum Card Number Reached",
                "After this turn, put all but $softMaxCards cards at the bottom of your deck.",
                CustomWarningParent.Severity.MIDDLE
            )
            this.permanentWarningId = id
            isPermanentWarningHard = false
        }
    }

    /**
     * shoots the revolver
     */
    @MainThreadOnly
    fun shoot() {
        val cardToShoot = revolver.getCardInSlot(5)
        val rotationDirection = cardToShoot?.rotationDirection ?: RevolverRotation.Right(1)
        val enemy = enemyArea.getTargetedEnemy()

        FortyFiveLogger.debug(logTag,
            "revolver is shooting;" +
                    "cardToShoot = $cardToShoot"
        )

        val damagePlayerTimeline = enemy.damagePlayerDirectly(shotEmptyDamage, this@GameController)

        val timeline = Timeline.timeline {
            includeLater(
                { damagePlayerTimeline },
                { cardToShoot == null }
            )
            cardToShoot?.let {
                action {
                    if (cardToShoot.shouldRemoveAfterShot) revolver.removeCard(cardToShoot)
                    cardToShoot.afterShot()
                }
                include(enemy.damage(cardToShoot.curDamage))
            }
            include(rotateRevolver(rotationDirection))
            cardToShoot?.let {
                include(checkEffectsSingleCard(Trigger.ON_SHOT, cardToShoot))
            }
        }

        appendMainTimeline(Timeline.timeline {
            parallelActions(
                timeline.asAction(),
                gameRenderPipeline.getOnShotPostProcessingTimelineAction()
            )
        })
    }

    fun tryApplyStatusEffectToEnemy(statusEffect: StatusEffect): Timeline = Timeline.timeline {
        if (encounterModifier.any { !it.shouldApplyStatusEffects() }) return Timeline(mutableListOf())
        action {
            enemyArea.getTargetedEnemy().applyEffect(statusEffect)
        }
    }

    fun rotateRevolver(rotation: RevolverRotation): Timeline = Timeline.timeline {
        val newRotation = modify(rotation) { modifier, cur -> modifier.modifyRevolverRotation(cur) }
//        include(revolver.rotate(newRotation))
        action {
            dispatchAnimTimeline(revolver.rotate(newRotation))
            revolverRotationCounter += newRotation.amount
            checkCardModifierValidity()
            revolver
                .slots
                .mapNotNull { it.card }
                .forEach(Card::onRevolverTurn)
        }
        if (newRotation.amount != 0) {
            val info = TriggerInformation(multiplier = newRotation.amount)
            include(checkEffectsActiveCards(Trigger.ON_REVOLVER_ROTATION, info))
        }
        include(enemyArea.getTargetedEnemy().executeStatusEffectsAfterRevolverRotation(newRotation))
        include(executePlayerStatusEffectsAfterRevolverRotation(newRotation))
    }

    private fun <T> modify(initial: T, transformer: (modifier: EncounterModifier, cur: T) -> T): T {
        var cur = initial
        encounterModifier.forEach { cur = transformer(it, cur) }
        return cur
    }

    @AllThreadsAllowed
    fun checkCardModifierValidity() {
        FortyFiveLogger.debug(logTag, "checking card modifiers")
        for (card in createdCards) if (card.inGame) card.checkModifierValidity()
    }

    @MainThreadOnly
    fun endTurn() {
        if (hasWon) {
            completeWin()
            return
        }
        appendMainTimeline(Timeline.timeline {
            include(checkEffectsActiveCards(Trigger.ON_ROUND_END))
            includeLater(
                { putCardsUnderDeckTimeline() },
                { cardHand.cards.size >= softMaxCards }
            )
            include(gameDirector.checkActions())
            include(executePlayerStatusEffectsOnNewTurn())
            action {
                nextTurn()
                curReserves = baseReserves
            }
            include(drawCardPopupTimeline(cardsToDraw))
            includeLater({ checkStatusEffectsAfterTurn() }, { true })
            includeLater({ checkEffectsActiveCards(Trigger.ON_ROUND_START) }, { true })
        })
    }

    private fun putCardsUnderDeckTimeline(): Timeline = Timeline.timeline {
        action {
            putCardsUnderDeckWidget.targetSize = cardHand.cards.size - softMaxCards
            curScreen.enterState(showPutCardsUnderDeckActorScreenState)
            cardHand.attachToActor("putCardsUnderDeckActor") // TODO: fix
            cardHand.unfreeze() // force cards to be draggable
        }
        delayUntil { putCardsUnderDeckWidget.isFinished }
        action {
            curScreen.leaveState(showPutCardsUnderDeckActorScreenState)
            cardHand.reattachToOriginalParent()
            val cards = putCardsUnderDeckWidget.complete()
            cards.forEach { cardStack.add(it) }
            _remainingCards = cardStack.size
        }
    }

    /**
     * damages the player
     */
    @AllThreadsAllowed
    fun damagePlayerTimeline(damage: Int, triggeredByStatusEffect: Boolean = false): Timeline = Timeline.timeline {
//        if (!triggeredByStatusEffect) {
//            val overlayAction = GraphicsConfig.damageOverlay(curScreen)
//            includeAction(overlayAction)
//        }
        action {
            curPlayerLives -= damage
            FortyFiveLogger.debug(
                logTag,
                "player got damaged; damage = $damage; curPlayerLives = $curPlayerLives"
            )
            if (curPlayerLives <= 0) playerDied()
        }
        if (!triggeredByStatusEffect) include(executePlayerStatusEffectsAfterDamage(damage))
    }

    /**
     * adds reserves (plays no animations)
     */
    @AllThreadsAllowed
    fun gainReserves(amount: Int) {
        curReserves += amount
        FortyFiveLogger.debug(logTag, "player gained reserves; amount = $amount; curReserves = $curReserves")
    }

    /**
     * destroys a card in the revolver
     */
    @MainThreadOnly
    fun destroyCardTimeline(card: Card): Timeline = Timeline.timeline {
        include(card.actor.destroyAnimation())
        action {
            revolver.removeCard(card)
            card.onDestroy()
            FortyFiveLogger.debug(logTag, "destroyed card: $card")
        }
        include(checkEffectsSingleCard(Trigger.ON_DESTROY, card))
    }

    @MainThreadOnly
    private fun checkEffectsSingleCard(
        trigger: Trigger,
        card: Card,
        triggerInformation: TriggerInformation = TriggerInformation()
    ): Timeline {
        FortyFiveLogger.debug(logTag, "checking effects for card $card, trigger $trigger")
        return card.checkEffects(trigger, triggerInformation, this)
    }

    @MainThreadOnly
    fun checkEffectsActiveCards(
        trigger: Trigger,
        triggerInformation: TriggerInformation = TriggerInformation()
    ): Timeline {
        FortyFiveLogger.debug(logTag, "checking all active cards for trigger $trigger")
        return createdCards
            .filter { it.inGame || it.inHand(this) }
            .map { it.checkEffects(trigger, triggerInformation, this) }
            .collectTimeline()
    }

    @MainThreadOnly
    fun checkStatusEffectsAfterTurn(): Timeline = enemyArea
        .enemies
        .map { it.executeStatusEffectsAfterTurn() }
        .collectTimeline()

    fun applyStatusEffectToPlayer(effect: StatusEffect) {
        FortyFiveLogger.debug(logTag, "status effect $effect applied to player")
        _playerStatusEffects
            .find { it.canStackWith(effect) }
            ?.let {
                FortyFiveLogger.debug(logTag, "stacked with $it")
                it.stack(effect)
                return
            }
        effect.start(this)
        effect.initIcon(this)
        _playerStatusEffects.add(effect)
        statusEffectDisplay.displayEffect(effect)
    }

    private fun executePlayerStatusEffectsAfterRevolverRotation(rotation: RevolverRotation): Timeline =
        _playerStatusEffects
            .mapNotNull { it.executeAfterRotation(rotation, StatusEffectTarget.PlayerTarget) }
            .collectTimeline()

    private fun executePlayerStatusEffectsAfterDamage(damage: Int): Timeline = _playerStatusEffects
        .mapNotNull { it.executeAfterDamage(damage, StatusEffectTarget.PlayerTarget) }
        .collectTimeline()

    private fun executePlayerStatusEffectsOnNewTurn(): Timeline = _playerStatusEffects
        .mapNotNull { it.executeOnNewTurn(StatusEffectTarget.PlayerTarget) }
        .collectTimeline()

    private fun updateStatusEffects() {
        _playerStatusEffects
            .filter { !it.isStillValid() }
            .forEach {
                statusEffectDisplay.removeEffect(it)
            }
        _playerStatusEffects.removeIf { !it.isStillValid() }
    }

    fun isStatusEffectApplicable(effect: StatusEffect): Boolean {
        val collision = _playerStatusEffects.find { it == effect } ?: return true
        return collision.canStackWith(effect)
    }

    fun appendMainTimeline(timeline: Timeline) {
        mainTimeline.appendAction(timeline.asAction())
    }

    fun dispatchAnimTimeline(timeline: Timeline) {
        animTimelinesAddBuffer.add(timeline)
    }

    private fun freezeUI() {
        isUIFrozen = true
        FortyFiveLogger.debug(logTag, "froze UI")
        cardHand.freeze()
        curScreen.enterState(freezeUIScreenState)
    }

    private fun unfreezeUI() {
        isUIFrozen = false
        FortyFiveLogger.debug(logTag, "unfroze UI")
        cardHand.unfreeze()
        curScreen.leaveState(freezeUIScreenState)
    }

    /**
     * draws a bullet from the stack
     */
    @AllThreadsAllowed
    fun drawCard() {
        val card = cardStack.removeFirstOrNull() ?: defaultBullet.create()
        _remainingCards = cardStack.size
        cardHand.addCard(card)
        FortyFiveLogger.debug(logTag, "card was drawn; card = $card; cardsToDraw = $cardsToDraw")
        cardsDrawn++
    }

    private fun cost(cost: Int): Boolean {
        if (cost > curReserves) return false
        curReserves -= cost
        SaveState.usedReserves += cost
        reservesSpent += cost
        FortyFiveLogger.debug(logTag, "$cost reserves were spent, curReserves = $curReserves")
        return true
    }

    override fun end() {
        createdCards.forEach { it.dispose() }
        gameDirector.end()
        FortyFiveLogger.title("game ends")
        FortyFive.currentGame = null
        if (playerLost) FortyFive.newRun()
        SaveState.write()
    }

    /**
     * called when an enemy was defeated
     */
    @MainThreadOnly
    fun enemyDefeated(enemy: Enemy) {
        SaveState.enemiesDefeated++
        hasWon = true
        FortyFiveLogger.debug(logTag, "player won")
    }

    @MainThreadOnly
    private fun completeWin() {
        appendMainTimeline(Timeline.timeline {
            val money = -enemyArea.enemies[0].currentHealth
            if (money > 0) {
                include(confirmationPopupTimeline("You won!\nYour overkill damage will be converted to $money$"))
            }
            action {
                SaveState.playerMoney += money
                encounterMapEvent.completed()
                SaveState.write()
                MapManager.changeToMapScreen()
            }
        })
    }

    @MainThreadOnly
    fun playerDied() {
        appendMainTimeline(Timeline.timeline {
            action {
                FortyFiveLogger.debug(logTag, "player lost")
                playerLost = true
            }
            includeAction(gameRenderPipeline.getOnDeathPostProcessingTimelineAction())
            action {
                FortyFive.changeToScreen("screens/title_screen.onj")
            }
        })
    }

    sealed class RevolverRotation {

        abstract val amount: Int

        class Right(override val amount: Int) : RevolverRotation() {

            override fun toString(): String = "Right($amount)"
        }
        class Left(override val amount: Int) : RevolverRotation() {

            override fun toString(): String = "Left($amount)"
        }
        object None : RevolverRotation() {

            override val amount: Int = 0

            override fun toString(): String = "None"
        }


        companion object {

            fun fromOnj(onj: OnjNamedObject): RevolverRotation = when (onj.name) {
                "Right" -> Right(onj.get<Long>("amount").toInt())
                "Left" -> Left(onj.get<Long>("amount").toInt())
                "Dont" -> None
                else -> throw RuntimeException("unknown revolver rotation: ${onj.name}")
            }

        }

    }

    companion object {

        const val logTag = "game"

        const val cardDrawActorScreenState = "showCardDrawActor"
        const val freezeUIScreenState = "uiFrozen"
        const val showPopupScreenState = "showPopup"
        const val showPopupConfirmationButtonScreenState = "showPopupConfirmationButton"
        const val showPopupCardSelectorScreenState = "showPopupCardSelector"
        const val showEnemyAttackPopupScreenState = "showAttackPopup"
        const val showPutCardsUnderDeckActorScreenState = "showPutCardsUnderDeckActor"

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

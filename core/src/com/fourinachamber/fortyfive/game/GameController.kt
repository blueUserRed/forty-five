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
import com.fourinachamber.fortyfive.screen.gameComponents.CardHand
import com.fourinachamber.fortyfive.screen.gameComponents.CircularCardSelector
import com.fourinachamber.fortyfive.screen.gameComponents.EnemyArea
import com.fourinachamber.fortyfive.screen.gameComponents.Revolver
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Integer.max

/**
 * the Controller for the main game screen
 */
class GameController(onj: OnjNamedObject) : ScreenController() {

    val gameDirector = GameDirector(this)

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private val enemyAreaOnj = onj.get<OnjObject>("enemyArea")
    private val cardSelectorOnj = onj.get<OnjObject>("cardSelector")

    val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()

    val baseReserves by templateParam(
        "game.baseReserves", onj.get<Long>("reservesAtRoundBegin").toInt()
    )

    val maxCards = onj.get<Long>("maxCards").toInt()
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

    private var cardPrototypes: List<CardPrototype> = listOf()
    val createdCards: MutableList<Card> = mutableListOf()
    private var cardStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var _remainingCards: Int by multipleTemplateParam(
        "game.cardsInStack", 0,
        "game.cardsInStackPluralS" to { if (it == 1) "" else "s" }
    )

    val remainingCards: Int
        get() = _remainingCards

    // currently not used, but might be usefull for an encouter modifier later
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

    lateinit var gameRenderPipeline: GameRenderPipeline
    private lateinit var encounterMapEvent: EncounterMapEvent

    var modifier: EncounterModifier? = null

    var reservesSpent: Int = 0
        private set

    var cardsDrawn: Int = 0
        private set

    private var hasWon: Boolean = false

    var playerLost: Boolean = false
        private set

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

        initCards()
        initCardHand()
        initRevolver()
        initCardSelector()
        // enemy area is initialised by the GameDirector
        gameDirector.init()
        appendMainTimeline(drawCardPopupTimeline(cardsToDrawInFirstRound))
        onjScreen.invalidateEverything()
    }

    private fun initCards() {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        cardPrototypes = Card
            .getFrom(onj.get<OnjArray>("cards"), curScreen, ::initCard)
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
        val behaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
            cardDragAndDropBehaviour.name,
            cardDragAndDrop,
            card.actor,
            cardDragAndDropBehaviour
        )
        cardDragAndDrop.addSource(behaviour)
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

    private var updateCount = 0 //TODO: this is stupid

    @MainThreadOnly
    override fun update() {
        if (updateCount == 3) curScreen.invalidateEverything() //TODO: this is stupid
        updateCount++

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
        if (mainTimeline.isFinished && isUIFrozen) unfreezeUI()
        if (!mainTimeline.isFinished && !isUIFrozen) freezeUI()

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

//    /**
//     * changes the game to the SpecialDraw phase and sets the amount of cards to draw to [amount]
//     */
//    @AllThreadsAllowed
//    fun specialDraw(amount: Int) {
//        if (currentState !is GameState.Free) return
//        changeState(GameState.SpecialDraw(amount))
//    }

    private fun initCardHand() {
        val curScreen = curScreen
        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActorOrError(cardHandName)
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        cardHand.init(this)
        this.cardHand = cardHand
    }

    private fun initRevolver() {
        val curScreen = curScreen
        val revolverName = revolverOnj.get<String>("actorName")
        val revolver = curScreen.namedActorOrError(revolverName)
        if (revolver !is Revolver) throw RuntimeException("actor named $revolverName must be a Revolver")
        val dropOnj = revolverOnj.get<OnjNamedObject>("dropBehaviour")
        revolver.initDragAndDrop(cardDragAndDrop to dropOnj)
        this.revolver = revolver
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
    fun loadBulletInRevolver(card: Card, slot: Int) {
        if (card.type != Card.Type.BULLET || !card.allowsEnteringGame(this)) return
        if (revolver.getCardInSlot(slot) != null) return
        if (!cost(card.cost)) return
        cardHand.removeCard(card)
        revolver.setCard(slot, card)
        FortyFiveLogger.debug(logTag, "card $card entered revolver in slot $slot")
        card.onEnter()
        appendMainTimeline(checkEffectsSingleCard(Trigger.ON_ENTER, card))
    }

    fun maxCardsPopupTimeline(): Timeline = confirmationPopupTimeline("Hand reached maximum of $maxCards cards")

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
            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
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
            if (remainingCardsToDraw != 0) curScreen.leaveState(cardDrawActorScreenState)
        }
    }

    fun enemyAttackTimeline(damage: Int): Timeline = Timeline.timeline {
        var parryCard: Card? = null
        action {
            parryCard = revolver.slots[4].card!!
            curScreen.enterState(showEnemyAttackPopupScreenState)
        }
        delayUntil { popupEvent != null || parryCard == null }
        includeLater(
            { Timeline.timeline {
                val parryCard = parryCard!!
                val remainingDamage = damage - parryCard.curDamage
                action {
                    revolverRotationCounter++
                }
                action {
                    popupEvent = null
                    curScreen.leaveState(showEnemyAttackPopupScreenState)
                    revolver.removeCard(parryCard)
                    parryCard.leaveGame()
                }
                include(revolver.rotate(parryCard.rotationDirection))
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
    fun putCardInHand(name: String) {
        val cardProto = cardPrototypes
            .firstOrNull { it.name == name }
            ?: throw RuntimeException("unknown card: $name")
        cardHand.addCard(cardProto.create())
        FortyFiveLogger.debug(logTag, "card $name entered hand")
    }

    /**
     * shoots the revolver
     */
    @MainThreadOnly
    fun shoot() {
        revolverRotationCounter++

        val cardToShoot = revolver.getCardInSlot(5)
        var rotationDirection = cardToShoot?.rotationDirection ?: RevolverRotation.Right(1)
        if (modifier != null) rotationDirection = modifier!!.modifyRevolverRotation(rotationDirection)
        val enemy = enemyArea.getTargetedEnemy()

        FortyFiveLogger.debug(logTag,
            "revolver is shooting;" +
                    "revolverRotationCounter = $revolverRotationCounter;" +
                    "cardToShoot = $cardToShoot"
        )

        var enemyDamageTimeline: Timeline? = null
        var damageStatusEffectTimeline: Timeline? = null
        var turnStatusEffectTimeline: Timeline? = null
        var effectTimeline: Timeline? = null

        if (cardToShoot != null) {

            enemyDamageTimeline = Timeline.timeline {
                action {
                    if (cardToShoot.shouldRemoveAfterShot) revolver.removeCard(cardToShoot)
                }
                include(enemy.damage(cardToShoot.curDamage))
                action { cardToShoot.afterShot() }
            }

            damageStatusEffectTimeline =
                enemy.executeStatusEffectsAfterDamage(cardToShoot.curDamage)
            turnStatusEffectTimeline = enemy.executeStatusEffectsAfterRevolverRotation()

            effectTimeline = cardToShoot.checkEffects(Trigger.ON_SHOT)
        }

        val finishTimeline = Timeline.timeline {
            action {

                checkCardModifierValidity()

                revolver
                    .slots
                    .mapNotNull { it.card }
                    .forEach(Card::onRevolverTurn)

                enemyArea.enemies.forEach(Enemy::onRevolverTurn)
            }
        }

        val damagePlayerTimeline = enemy.damagePlayerDirectly(shotEmptyDamage, this@GameController)

        val timeline = Timeline.timeline {

            includeLater(
                { damagePlayerTimeline },
                { cardToShoot == null }
            )

            enemyDamageTimeline?.let { include(it) }

            includeLater(
                { damageStatusEffectTimeline!! },
                { damageStatusEffectTimeline != null }
            )
            includeLater(
                { effectTimeline!! },
                { effectTimeline != null }
            )

            action {
                FortyFiveLogger.debug(logTag, "revolver rotated $rotationDirection")
            }

            includeLater(
                { turnStatusEffectTimeline!! },
                { turnStatusEffectTimeline != null }
            )

            includeLater(
                { finishTimeline },
                { true }
            )

        }

        appendMainTimeline(Timeline.timeline {
            parallelActions(
                timeline.asAction(),
                gameRenderPipeline.getOnShotPostProcessingTimelineAction(),
                revolver.rotate(rotationDirection).asAction()
            )
        })
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
            include(gameDirector.checkActions())
            action {
                nextTurn()
                curReserves = baseReserves
            }
            include(drawCardPopupTimeline(cardsToDraw))
            includeLater({ checkStatusEffectsAfterTurn() }, { true })
            includeLater({ checkEffectsActiveCards(Trigger.ON_ROUND_START) }, { true })
        })
    }

    /**
     * damages the player (plays no animation, calls loose when lives go below 0)
     */
    @AllThreadsAllowed
    fun damagePlayerTimeline(damage: Int): Timeline = Timeline.timeline {
        val overlayAction = GraphicsConfig.damageOverlay(curScreen)
        includeAction(overlayAction)
        action {
            curPlayerLives -= damage
            FortyFiveLogger.debug(
                logTag,
                "player got damaged; damage = $damage; curPlayerLives = $curPlayerLives"
            )
            if (curPlayerLives <= 0) playerDied()
        }
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
        action {
            revolver.removeCard(card)
            card.onDestroy()
            FortyFiveLogger.debug(logTag, "destroyed card: $card")
        }
        include(checkEffectsSingleCard(Trigger.ON_DESTROY, card))
    }

    @MainThreadOnly
    private fun checkEffectsSingleCard(trigger: Trigger, card: Card): Timeline {
        FortyFiveLogger.debug(logTag, "checking effects for card $card, trigger $trigger")
        return card.checkEffects(trigger) ?: Timeline(mutableListOf())
    }

    @MainThreadOnly
    fun checkEffectsActiveCards(trigger: Trigger): Timeline {
        FortyFiveLogger.debug(logTag, "checking all active cards for trigger $trigger")
        val timeline = Timeline.timeline {
            for (card in createdCards) if (card.inGame) {
                val timeline = card.checkEffects(trigger)
                if (timeline != null) include(timeline)
            }
        }
        return timeline
    }

    @MainThreadOnly
    fun checkStatusEffectsAfterTurn(): Timeline {
        FortyFiveLogger.debug(logTag, "checking status effects")
        val timeline = Timeline.timeline {
            for (enemy in enemyArea.enemies) {
                val timeline = enemy.executeStatusEffectsAfterTurn()
                if (timeline != null) include(timeline)
            }
        }
        return timeline
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
        for (card in cardHand.cards) card.isDraggable = false
        curScreen.enterState(freezeUIScreenState)
    }

    private fun unfreezeUI() {
        isUIFrozen = false
        FortyFiveLogger.debug(logTag, "unfroze UI")
        for (card in cardHand.cards) card.isDraggable = true
        curScreen.leaveState(freezeUIScreenState)
    }

    @AllThreadsAllowed
    fun showCardDrawActor() {
        FortyFiveLogger.debug(logTag, "displaying card draw actor")
        curScreen.enterState(cardDrawActorScreenState)
    }

    @AllThreadsAllowed
    fun hideCardDrawActor() {
        FortyFiveLogger.debug(logTag, "hiding card draw actor")
        curScreen.leaveState(cardDrawActorScreenState)
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
        encounterMapEvent.completed()
        SaveState.write()
        MapManager.switchToMapScreen()
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
        class Right(val amount: Int) : RevolverRotation() {

            override fun toString(): String = "Right($amount)"
        }
        class Left(val amount: Int) : RevolverRotation() {

            override fun toString(): String = "Left($amount)"
        }
        object None : RevolverRotation() {

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

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.game.card.Trigger
import com.fourinachamber.fortyfive.game.card.TriggerInformation
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.rendering.RenderPipeline
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameWidgets.*
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import ktx.actors.onClick
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import onj.value.OnjString
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * the Controller for the main game screen
 */
class GameController(val screen: OnjScreen, onj: OnjNamedObject) : ScreenController(), ResourceBorrower {

    val gameDirector = GameDirector(this)

    private val cardDragBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
    private val cardDropBehaviour = onj.get<OnjNamedObject>("cardDropBehaviour")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private val warningParentName = onj.get<String>("warningParentName")
    private val statusEffectDisplayName = onj.get<String>("statusEffectDisplayName")
    private val putCardsUnderDeckWidgetOnj = onj.get<OnjObject>("putCardsUnderDeckWidget")
    private val encounterModifierDisplayTemplateName = onj.get<String>("encounterModifierDisplayTemplateName")

    val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()

    val baseReserves by templateParam(
        "game.baseReserves", onj.get<Long>("reservesAtRoundBegin").toInt()
    )

    val softMaxCards = onj.get<Long>("softMaxCards").toInt()
    val hardMaxCards = onj.get<Long>("hardMaxCards").toInt()

    private val rewardChance = onj.get<Double>("rewardChance").toFloat()
    private val rewardRerollBasePrice = onj.get<Long>("rewardRerollBasePrice").toInt()
    private val rewardRerollPriceIncrease = onj.get<Long>("rewardRerollPriceIncrease").toInt()

    private val shotEmptyDamage = onj.get<Long>("shotEmptyDamage").toInt()

    @Inject lateinit var cardHand: CardHand
        private set
    @Inject lateinit var revolver: Revolver
        private set
    @Inject lateinit var enemyArea: EnemyArea
        private set
    @Inject lateinit var putCardsUnderDeckWidget: PutCardsUnderDeckWidget
        private set
    @Inject lateinit var statusEffectDisplay: StatusEffectDisplay
        private set
    @Inject lateinit var tutorialInfoActor: TutorialInfoActor
        private set
    @Inject lateinit var shootButton: CustomFlexBox
        private set

    @Inject(name = "WARNING_PARENT")
    lateinit var warningParent: CustomWarningParent
        private set

    private var cardPrototypes: List<CardPrototype> = listOf()
    val createdCards: MutableList<Card> = mutableListOf()

    private var _cardStack: MutableList<Card> = mutableListOf()
    val cardStack: List<Card>
        get() = _cardStack

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

    private var popupText: String by templateParam("game.popupText", "")
    private var popupButtonText: String by templateParam("game.popupButtonText", "")
    private var popupEvent: Event? = null

    private val mainTimeline: Timeline = Timeline(mutableListOf()).apply { startTimeline() }

    private val animTimelines: MutableList<Timeline> = mutableListOf()
    private val animTimelinesAddBuffer: MutableList<Timeline> = mutableListOf()

    var isUIFrozen: Boolean = false
        private set

    private var selectedCard: Card? = null

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
        private set

    lateinit var encounterContext: EncounterContext
        private set

    private val _encounterModifiers: MutableList<Pair<((GameController) -> Boolean)?, EncounterModifier>> = mutableListOf()

    val encounterModifiers: List<EncounterModifier>
        get() = _encounterModifiers.map { it.second }

    var reservesSpent: Int = 0
        private set

    var cardsDrawn: Int = 0
        private set

    var hasWon: Boolean = false
        private set

    var playerLost: Boolean = false
        private set

    private val _playerStatusEffects: MutableList<StatusEffect> = mutableListOf()

    val playerStatusEffects: List<StatusEffect>
        get() = _playerStatusEffects

    private var permanentWarningId: Int? = null
    private var isPermanentWarningHard: Boolean = false

    private val tutorialTextParts: MutableList<GameDirector.GameTutorialTextPart> = mutableListOf()
    private var currentlyShowingTutorialText: Boolean = false

    val activeEnemies: List<Enemy>
        get() = enemyArea.enemies.filter { !it.isDefeated }

    val inFreePhase: Boolean
        get() = !isUIFrozen

    @MainThreadOnly
    override fun init(context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterContext = context
        FortyFive.currentGame = this
        gameRenderPipeline = GameRenderPipeline(screen)
        FortyFiveLogger.title("game starting")

        gameDirector.init()

        initCards()
        cardHand.init(this)
        initRevolver()
        initPutCardsUnderDeckWidget()
        // enemy area is initialised by the GameDirector

        curReserves = baseReserves

        if (enemyArea.enemies.size > 1 && !PermaSaveState.playerFoughtMultipleEnemies) {
            // TODO: hack
            addTutorialText(listOf(
                GameDirector.GameTutorialTextPart(
                "In this encounter, you will fight multiple enemies. You can choose which one to attack by clicking" +
                        " on the enemy.",
                "Ok",
                null,
                null
            )))
            PermaSaveState.playerFoughtMultipleEnemies = true
        }

        encounterModifiers.forEach { it.onStart(this) }
        appendMainTimeline(Timeline.timeline {
            include(drawCardPopupTimeline(cardsToDrawInFirstRound))
            encounterModifiers
                    .mapNotNull { it.executeOnPlayerTurnStart(this@GameController) }
                    .collectTimeline()
                    .let { include(it) }
        })
        screen.invalidateEverything()
        gameDirector.chooseEnemyActions()
    }

    override fun onShow() {
        FortyFive.useRenderPipeline(gameRenderPipeline)
        SoundPlayer.changeMusicTo(SoundPlayer.Theme.BATTLE, 5_000)
    }

    private fun initCards() {
        val onj = ConfigFileManager.getConfigFile("cards")

        val cards = gameDirector.encounter.forceCards
            ?: encounterContext.forceCards
            ?: SaveState.curDeck.cards

        val cardsArray = onj.get<OnjArray>("cards")

        cardPrototypes = Card
            .getFrom(cardsArray, ::initCard)
            .toMutableList()

        cards.forEach { cardName ->
            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            _cardStack.add(card.create(this.screen))
        }

        if (gameDirector.encounter.shuffleCards) _cardStack.shuffle()
        validateCardStack()

        FortyFiveLogger.debug(logTag, "card stack: $_cardStack")

        val defaultBulletName = onj.get<String>("defaultBullet")

        defaultBullet = cardPrototypes
            .filter { it.type == Card.Type.BULLET }
            .firstOrNull { it.name == defaultBulletName }
            ?: throw RuntimeException("unknown default bullet: $defaultBulletName")

    }

    fun addTutorialText(textParts: List<GameDirector.GameTutorialTextPart>) {
        tutorialTextParts.addAll(textParts)
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
        encounterModifiers.forEach { it.initBullet(card) }
    }

    override fun onUnhandledEvent(event: Event) = when (event) {
        is ShootRevolverEvent -> {
            shoot()
        }
        is EndTurnEvent -> {
            endTurn()
        }
        is TutorialConfirmedEvent -> {
            hideTutorialPopupActor()
        }
        is PopupConfirmationEvent, is PopupSelectionEvent, is DrawCardEvent, is ParryEvent -> {
            popupEvent = event
        }
        else -> { }
    }

    @MainThreadOnly
    override fun update() {
        _encounterModifiers.removeIf { it.first?.invoke(this)?.not() ?: false }
        encounterModifiers.forEach { it.update(this) }

        if (mainTimeline.isFinished && isUIFrozen) unfreezeUI()
        if (!mainTimeline.isFinished && !isUIFrozen) freezeUI()

        _remainingCards = _cardStack.size

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

        createdCards.flatMap { it.passiveEffects }.forEach { it.checkActive(this) }
        createdCards.forEach { it.update(this) }
        updateStatusEffects()
        updateGameAnimations()
        updateTutorialText()
    }

    private fun updateTutorialText() {
        if (currentlyShowingTutorialText) return
        if (tutorialTextParts.isEmpty()) return
        val nextPart = tutorialTextParts.first()
        if (nextPart.predicate == null || nextPart.predicate.check(this)) {
            showTutorialPopupActor(nextPart)
        }
    }

    private fun showTutorialPopupActor(tutorialTextPart: GameDirector.GameTutorialTextPart) {
        FortyFiveLogger.debug(logTag, "showing tutorial popup: ${tutorialTextPart.text}")
        currentlyShowingTutorialText = true
        this.screen.enterState(showTutorialActorScreenState)
        (this.screen.namedActorOrError("tutorial_info_text") as AdvancedTextWidget).setRawText(tutorialTextPart.text, listOf())
        TemplateString.updateGlobalParam("game.tutorial.confirmButtonText", tutorialTextPart.confirmationText)
        if (tutorialTextPart.focusActorName == null) {
            tutorialInfoActor.removeFocus()
        } else {
            tutorialInfoActor.focusActor(tutorialTextPart.focusActorName)
        }
    }

    private fun hideTutorialPopupActor() {
        FortyFiveLogger.debug(logTag, "hiding tutorial popup")
        currentlyShowingTutorialText = false
        this.screen.leaveState(showTutorialActorScreenState)
        tutorialTextParts.removeFirst()
        updateTutorialText() // prevents the tutorial popup from flickering for one frame
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

    fun titleOfCard(cardName: String): String = cardPrototypes.find { it.name == cardName }?.title
        ?: throw RuntimeException("no card with name $cardName")

    /**
     * plays a gameAnimation
     */
    @AllThreadsAllowed
    fun playGameAnimation(anim: GameAnimation) {
        FortyFiveLogger.debug(logTag, "playing game animation: $anim")
        anim.start()
        curGameAnims.add(anim)
    }

    private fun initRevolver() {
        val dropOnj = revolverOnj.get<OnjNamedObject>("dropBehaviour")
        revolver.initDragAndDrop(cardDragAndDrop to dropOnj)
    }

    private fun initPutCardsUnderDeckWidget() {
        val dropOnj = putCardsUnderDeckWidgetOnj.get<OnjNamedObject>("dropBehaviour")
        putCardsUnderDeckWidget.initDragAndDrop(cardDragAndDrop, dropOnj)
    }

    fun initEnemyArea(enemies: List<Enemy>) {
        enemies.forEach { enemy ->
            enemyArea.addEnemy(enemy)
            enemy.actor.onClick { enemyArea.selectEnemy(enemy) }
        }
        if (enemies.isEmpty()) throw RuntimeException("enemyArea must have at least one enemy")
    }

    fun addEncounterModifier(modifier: EncounterModifier) {
        TODO()
//        _encounterModifiers.add(null to modifier)
//        this.screen.screenBuilder.generateFromTemplate(
//            encounterModifierDisplayTemplateName,
//            mapOf(
//                "symbol" to OnjString(GraphicsConfig.encounterModifierIcon(modifier)),
//                "modifierName" to OnjString(GraphicsConfig.encounterModifierDisplayName(modifier)),
//                "modifierDescription" to OnjString(GraphicsConfig.encounterModifierDescription(modifier)),
//            ),
//            encounterModifierParent,
//            this.screen
//        )!!
    }

    fun addTemporaryEncounterModifier(modifier: EncounterModifier, validityChecker: (GameController) -> Boolean) {
        _encounterModifiers.add(validityChecker to modifier)
    }

    /**
     * puts [card] in [slot] of the revolver (checks if the card is a bullet)
     */
    @MainThreadOnly
    fun loadBulletInRevolver(card: Card, slot: Int) = appendMainTimeline(Timeline.timeline {
        var cardInSlot: Card? = null
        var skip = false
        action {
            FortyFiveLogger.debug(logTag, "attempting to load bullet $card in revolver slot $slot")
            cardInSlot = revolver.getCardInSlot(slot)
            if (
                card.type != Card.Type.BULLET ||
                !card.allowsEnteringGame(this@GameController, slot) ||
                !(cardInSlot?.isReplaceable ?: true) ||
                !cost(card.curCost(this@GameController), card.actor)
            ) {
                SoundPlayer.situation("not_allowed", this@GameController.screen)
                skip = true
                return@action
            }
            cardHand.removeCard(card)
            if (cardInSlot != null) revolver.preAddCard(slot, card)
        }
        includeLater(
            { destroyCardTimeline(cardInSlot!!) },
            { !skip && cardInSlot != null }
        )
        action {
            if (skip) return@action
            revolver.setCard(slot, card)
            FortyFiveLogger.debug(logTag, "card $card entered revolver in slot $slot")
            card.onEnter(this@GameController)
            checkCardMaximums()
        }
        includeLater(
            {
                encounterModifiers
                    .mapNotNull { it.executeAfterBulletWasPlacedInRevolver(card, this@GameController) }
                    .collectTimeline()
            },
            { !skip }
        )
        val triggerInfo = TriggerInformation(sourceCard = card, controller = this@GameController)
        includeLater(
            { checkEffectsSingleCard(Trigger.ON_ENTER, card, triggerInfo) },
            { !skip }
        )
        includeLater(
            { checkEffectsActiveCards(Trigger.ON_ANY_CARD_ENTER, triggerInfo) },
            { !skip }
        )
    })

    fun putCardFromRevolverBackInHand(card: Card) {
        FortyFiveLogger.debug(logTag, "returning card $card from the revolver to the hand")
        revolver.removeCard(card)
        card.leaveGame()
        cardHand.addCard(card)
        checkCardMaximums()
    }

    fun destroyCardInHand(card: Card) {
        FortyFiveLogger.debug(logTag, "destroying card $card in the hand")
        cardHand.removeCard(card)
        checkCardMaximums()
    }

    private fun maxCardsPopupTimeline(): Timeline =
        confirmationPopupTimeline("Hand reached maximum of $hardMaxCards cards")

    fun confirmationPopupTimeline(text: String): Timeline = Timeline.timeline {
        action {
            this@GameController.screen.enterState(showPopupScreenState)
            this@GameController.screen.enterState(showPopupConfirmationButtonScreenState)
            popupText = text
            popupButtonText = "Ok"
        }
        delayUntil { popupEvent != null }
        action {
            popupEvent = null
            this@GameController.screen.leaveState(showPopupScreenState)
            this@GameController.screen.leaveState(showPopupConfirmationButtonScreenState)
        }
    }

    fun cardSelectionPopupTimeline(text: String, exclude: Card? = null): Timeline = Timeline.timeline {
        action {
            revolver
                .slots
                .mapNotNull { it.card }
                .filter { it !== exclude }
                .forEach { it.actor.enterSelectionMode() }
            TemplateString.updateGlobalParam("game.revolverPopupText", text)
            this@GameController.screen.enterState(showSelectionPopup)
            selectedCard = null
        }
        delayUntil { selectedCard != null }
        action {
            revolver
                .slots
                .mapNotNull { it.card }
                .forEach { it.actor.exitSelectionMode() }
            store("selectedCard", selectedCard!!)
            this@GameController.screen.leaveState(showSelectionPopup)
            selectedCard = null
        }
    }

    fun selectCard(card: Card) {
        selectedCard = card
    }

    fun drawCardPopupTimeline(amount: Int, isSpecial: Boolean = true, fromBottom: Boolean = false): Timeline = Timeline.timeline {
        if (amount <= 0) return@timeline
        var remainingCardsToDraw = amount
        action {

            remainingCardsToDraw += encounterModifiers.sumOf {
                if (isSpecial) it.additionalCardsToDrawInSpecialDraw() else it.additionalCardsToDrawInNormalDraw()
            }

            remainingCardsToDraw = floor(
                encounterModifiers
                    .fold(remainingCardsToDraw.toFloat()) { acc, cur ->
                        acc * (if (isSpecial) cur.cardsInSpecialDrawMultiplier() else cur.cardsInNormalDrawMultiplier())
                    }
            ).toInt()

            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(hardMaxCards - cardHand.cards.size)
            FortyFiveLogger.debug(logTag, "drawing cards: remainingCards = $remainingCardsToDraw; isSpecial = $isSpecial")
            if (remainingCardsToDraw != 0) this@GameController.screen.enterState(cardDrawActorScreenState)
            TemplateString.updateGlobalParam(
                "game.drawCardText",
                "draw ${remainingCardsToDraw pluralS "card"} ${if (fromBottom) "from the bottom of your deck" else ""}"
            )
        }
        includeLater({ maxCardsPopupTimeline() }, { remainingCardsToDraw == 0 })
        includeLater({ Timeline.timeline {
            repeat(remainingCardsToDraw) { cur ->
                delayUntil { popupEvent != null }
                action {
                    SoundPlayer.situation("card_drawn", this@GameController.screen)
                    popupEvent = null
                    TemplateString.updateGlobalParam(
                        "game.drawCardText",
                        "draw ${(remainingCardsToDraw - cur - 1) pluralS "card"} ${if (fromBottom) "from the bottom of your deck" else ""}"
                    )
                }
                include(drawCard(fromBottom))
            }
        }}, { remainingCardsToDraw != 0 })
        action {
            if (remainingCardsToDraw == 0) return@action
            this@GameController.screen.leaveState(cardDrawActorScreenState)
            checkCardMaximums()
        }
        val cardsDrawnTriggerInfo = TriggerInformation(multiplier = remainingCardsToDraw, amountOfCardsDrawn = remainingCardsToDraw, controller = this@GameController)
        val oneOrMoreDrawnTriggerInfo = TriggerInformation(amountOfCardsDrawn = remainingCardsToDraw, controller = this@GameController)
        includeLater({ checkEffectsActiveCards(Trigger.ON_CARDS_DRAWN, cardsDrawnTriggerInfo) }, { remainingCardsToDraw != 0 })
        includeLater(
            { checkEffectsActiveCards(Trigger.ON_SPECIAL_CARDS_DRAWN, cardsDrawnTriggerInfo) },
            { isSpecial && remainingCardsToDraw != 0 }
        )
        includeLater(
            { checkEffectsActiveCards(Trigger.ON_ONE_OR_MORE_CARDS_DRAWN, oneOrMoreDrawnTriggerInfo) },
            { remainingCardsToDraw != 0 }
        )
        includeLater(
            { checkEffectsActiveCards(Trigger.ON_SPECIAL_ONE_OR_MORE_CARDS_DRAWN, oneOrMoreDrawnTriggerInfo) },
            { isSpecial && remainingCardsToDraw != 0 }
        )
    }

    fun enemyAttackTimeline(damage: Int, isPiercing: Boolean = false): Timeline = Timeline.timeline {
        var parryCard: Card? = null
        var remainingDamage: Int? = null
        action {
            parryCard = revolver.slots[4].card ?: return@action
            remainingDamage = if (parryCard!!.isReinforced) {
                0
            } else {
                damage - parryCard!!.curDamage(this@GameController)
            }
            TemplateString.updateGlobalParam("game.remainingParryDamage", max(remainingDamage!!, 0))
            TemplateString.updateGlobalParam("game.remainingPassDamage", max(damage, 0))
            TemplateString.updateGlobalParam("game.revolverPopupText", "Parry Bullet?")
            gameRenderPipeline.startParryEffect()
            this@GameController.screen.enterState(showEnemyAttackPopupScreenState)
            FortyFiveLogger.debug(logTag, "enemy attacking: damage = $damage; parryCard = $parryCard")
            SoundPlayer.situation("enter_parry", this@GameController.screen)
        }
        delayUntil { popupEvent != null || parryCard == null }
        includeLater(
            { Timeline.timeline {
                @Suppress("NAME_SHADOWING") val parryCard = parryCard!!
                action {
                    popupEvent = null
                    FortyFiveLogger.debug(logTag, "Player parried")
                }
                includeLater (
                    { checkEffectsSingleCard(Trigger.ON_LEAVE, parryCard, TriggerInformation(isOnShot = true, controller = this@GameController)) },
                    { parryCard.shouldRemoveAfterShot(this@GameController) }
                )
                action {
                    this@GameController.screen.leaveState(showEnemyAttackPopupScreenState)
                    gameRenderPipeline.stopParryEffect()
                    if (parryCard.shouldRemoveAfterShot(this@GameController)) {
                        if (!parryCard.isUndead) {
                            SoundPlayer.situation("orb_anim_playing", this@GameController.screen)
                            gameRenderPipeline.addOrbAnimation(cardOrbAnim(parryCard.actor))
                        }
                        revolver.removeCard(parryCard)
                        if (parryCard.isUndead) {
                            cardHand.addCard(parryCard)
                        } else {
                            putCardAtBottomOfStack(parryCard)
                        }
                    }
                    parryCard.afterShot(this@GameController)
                }
                include(rotateRevolver(parryCard.rotationDirection))
                if (remainingDamage!! > 0) {
                    action {
                        SoundPlayer.situation("enemy_attack", this@GameController.screen)
                    }
                    includeLater(
                        { damagePlayerTimeline(remainingDamage!!, isPiercing = isPiercing) },
                        { true }
                    )
                }
            } },
            { popupEvent is ParryEvent && parryCard != null }
        )
        includeLater(
            { Timeline.timeline {
                action {
                    popupEvent = null
                    this@GameController.screen.leaveState(showEnemyAttackPopupScreenState)
                    gameRenderPipeline.stopParryEffect()
                    FortyFiveLogger.debug(logTag, "Player didn't parry")
                    SoundPlayer.situation("enemy_attack", this@GameController.screen)
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
            if (cardsToDraw == 0) return@action
            val cardProto = cardPrototypes
                .firstOrNull { it.name == name }
                ?: throw RuntimeException("unknown card: $name")
            repeat(cardsToDraw) {
                cardHand.addCard(cardProto.create(this@GameController.screen))
            }
            FortyFiveLogger.debug(logTag, "card $name entered hand $amount times")
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
                this.screen,
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
                this.screen,
                "Maximum Card Number Reached",
                "After this turn, put all but $softMaxCards cards at the bottom of your deck.",
                CustomWarningParent.Severity.MIDDLE
            )
            this.permanentWarningId = id
            isPermanentWarningHard = false
        } else if (permanentWarningId != null) {
            warningParent.removePermanentWarning(permanentWarningId)
        }
    }

    /**
     * shoots the revolver
     */
    @MainThreadOnly
    fun shoot() {

        if (encounterModifiers.any { !it.canShootRevolver(this) }) return

        val cardToShoot = revolver.getCardInSlot(5)
        val rotationDirection = cardToShoot?.rotationDirection ?: RevolverRotation.Right(1)

        FortyFiveLogger.debug(logTag,
            "revolver is shooting;" +
                    "cardToShoot = $cardToShoot"
        )

        if (cardToShoot?.canBeShot(this)?.not() ?: false) {
            FortyFiveLogger.debug(logTag, "Card can't be shot because it blocks")
            return
        }

        val targetedEnemies = if (cardToShoot?.isSpray ?: false) {
            enemyArea.enemies
        } else {
            listOf(enemyArea.getTargetedEnemy())
        }

        val damagePlayerTimeline = enemyArea
            .getTargetedEnemy()
            .damagePlayerDirectly(shotEmptyDamage, this@GameController)

        val timeline = Timeline.timeline {
            action {
                SoundPlayer.situation("revolver_shot", this@GameController.screen)
            }
            includeLater(
                { damagePlayerTimeline },
                { cardToShoot == null }
            )
            cardToShoot?.let {
                action {
                    SaveState.bulletsShot++
                    cardToShoot.beforeShot()
                }
                targetedEnemies
                    .map { it.damage(cardToShoot.curDamage(this@GameController)) }
                    .collectTimeline()
                    .let { include(it) }
            }
            cardToShoot?.let {
                val triggerInformation = TriggerInformation(targetedEnemies = targetedEnemies, isOnShot = true, controller = this@GameController)
                include(checkEffectsSingleCard(Trigger.ON_SHOT, cardToShoot, triggerInformation))
                includeLater(
                    { checkEffectsSingleCard(Trigger.ON_LEAVE, cardToShoot, triggerInformation) },
                    { cardToShoot.shouldRemoveAfterShot(this@GameController) }
                )
                action {
                    if (cardToShoot.shouldRemoveAfterShot(this@GameController)) {
                        if (!cardToShoot.isUndead) {
                            putCardAtBottomOfStack(cardToShoot)
                            SoundPlayer.situation("orb_anim_playing", this@GameController.screen)
                            gameRenderPipeline.addOrbAnimation(cardOrbAnim(cardToShoot.actor))
                        }
                        revolver.removeCard(cardToShoot)
                    }
                    if (cardToShoot.isUndead) cardHand.addCard(cardToShoot)
                    cardToShoot.afterShot(this@GameController)
                }
            }
            include(rotateRevolver(rotationDirection))
            encounterModifiers
                .mapNotNull { it.executeAfterRevolverWasShot(cardToShoot, this@GameController) }
                .collectTimeline()
                .let { include(it) }
        }

        appendMainTimeline(Timeline.timeline {
            parallelActions(
                timeline.asAction(),
                gameRenderPipeline.getOnShotPostProcessingTimeline().asAction()
            )
        })
    }

    private fun cardOrbAnim(actor: Actor, reverse: Boolean = false): RenderPipeline.OrbAnimation {
        val actorCoords = actor.localToStageCoordinates(Vector2(0f, 0f)) +
                Vector2(actor.width / 2, actor.height / 2)
        val deckCoords = this.screen.centeredStageCoordsOfActor("deck_icon")
        val source = if (reverse) deckCoords else actorCoords
        val target = if (reverse) actorCoords else deckCoords
        return GraphicsConfig.orbAnimation(source, target, false, gameRenderPipeline)
    }

    fun tryApplyStatusEffectToEnemy(statusEffect: StatusEffect, enemy: Enemy): Timeline = Timeline.timeline {
        if (encounterModifiers.any { !it.shouldApplyStatusEffects() }) return Timeline()
        action {
            enemy.applyEffect(statusEffect)
        }
    }

    fun rotateRevolver(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean = false
    ): Timeline = Timeline.timeline {
        var newRotation = if (ignoreEncounterModifiers) {
            rotation
        } else {
            modifiers(rotation) { modifier, cur -> modifier.modifyRevolverRotation(cur) }
        }
        playerStatusEffects.forEach { newRotation = it.modifyRevolverRotation(newRotation) }
        include(revolver.rotate(newRotation))
        action {
            revolverRotationCounter += newRotation.amount
            revolver
                .slots
                .mapNotNull { it.card }
                .forEach { it.onRevolverRotation(newRotation) }
        }
        encounterModifiers
            .mapNotNull { it.executeAfterRevolverRotated(newRotation, this@GameController) }
            .collectTimeline()
            .let { include(it) }
        if (newRotation.amount != 0) {
            val info = TriggerInformation(multiplier = newRotation.amount, controller = this@GameController)
            include(checkEffectsActiveCards(Trigger.ON_REVOLVER_ROTATION, info))
            includeLater(
                {
                    revolver
                        .slots
                        .asList()
                        .zip { it.card }
                        .filter { it.second != null }
                        .filter { it.first.num == it.second?.enteredInSlot }
                        .map { checkEffectsSingleCard(Trigger.ON_RETURNED_HOME, it.second!!) }
                        .collectTimeline()
                },
                { true }
            )
            includeLater(
                {
                    checkEffectsSingleCard(Trigger.ON_ROTATE_IN_5, revolver.getCardInSlot(5)!!)
                },
                { revolver.getCardInSlot(5) != null }
            )
        }
        enemyArea
            .enemies
            .map { it.executeStatusEffectsAfterRevolverRotation(newRotation) }
            .collectTimeline()
            .let { include(it) }
        include(executePlayerStatusEffectsAfterRevolverRotation(newRotation))
    }

    private fun <T> modifiers(initial: T, transformer: (modifier: EncounterModifier, cur: T) -> T): T {
        var cur = initial
        encounterModifiers.forEach { cur = transformer(it, cur) }
        return cur
    }

    @MainThreadOnly
    fun endTurn() {
        if (hasWon) {
            completeWin()
            return
        }
        appendMainTimeline(Timeline.timeline {
            action {
                SoundPlayer.situation("end_turn", this@GameController.screen)
            }
            encounterModifiers
                    .mapNotNull { it.executeOnEndTurn() }
                    .collectTimeline()
                    .let { include(it) }
            include(checkEffectsActiveCards(Trigger.ON_ROUND_END))
            includeLater(
                { putCardsUnderDeckTimeline() },
                { cardHand.cards.size >= softMaxCards }
            )
            action {
                turnCounter++
            }
            include(bannerAnimationTimeline(false))
            include(gameDirector.checkActions())
            include(executePlayerStatusEffectsOnNewTurn())
            action {
                gameDirector.chooseEnemyActions()
                SoundPlayer.situation("turn_begin", this@GameController.screen)
            }
            include(bannerAnimationTimeline(true))
            action {
                curReserves = baseReserves
            }
            include(drawCardPopupTimeline(cardsToDraw, false))
            encounterModifiers
                    .mapNotNull { it.executeOnPlayerTurnStart(this@GameController) }
                    .collectTimeline()
                    .let { include(it) }
            includeLater({ checkStatusEffectsAfterTurn() }, { true })
            includeLater({ checkEffectsActiveCards(Trigger.ON_ROUND_START) }, { true })
        })
    }

    private val enemyBannerPromise: Promise<Drawable> =
        ResourceManager.request(this, this.screen, "enemy_turn_banner")

    private val playerBannerPromise: Promise<Drawable> =
        ResourceManager.request(this, this.screen, "player_turn_banner")

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

    private val shieldIconPromise: Promise<Drawable> =
        ResourceManager.request(this, this.screen, "shield_icon_large")

    private val shieldShaderPromise: Promise<BetterShader> =
        ResourceManager.request(this, this.screen, "glow_shader_shield")

    private fun shieldAnimationTimeline(): Timeline {
        val shieldIcon = shieldIconPromise.getOrNull() ?: return Timeline()
        val shieldShader = shieldShaderPromise.getOrNull() ?: return Timeline()
        return Timeline.timeline {
            val bannerAnim = BannerAnimation(
                shieldIcon,
                this@GameController.screen,
                1_000,
                150,
                0.3f,
                0.5f,
                interpolation = Interpolation.pow2In,
                customShader = shieldShader
            ).asTimeline(this@GameController).asAction()
            val postProcessorAction = Timeline.timeline {
                delay(100)
                include(gameRenderPipeline.getScreenShakePopoutTimeline())
                delay(50)
                action { SoundPlayer.situation("shield_anim", this@GameController.screen) }
            }.asAction()
            parallelActions(bannerAnim, postProcessorAction)
        }
    }

    private fun putCardsUnderDeckTimeline(): Timeline = Timeline.timeline {
        action {
            putCardsUnderDeckWidget.targetSize = cardHand.cards.size - softMaxCards
            this@GameController.screen.enterState(showPutCardsUnderDeckActorScreenState)
            cardHand.attachToActor("putCardsUnderDeckActor") // TODO: fix
            cardHand.unfreeze() // force cards to be draggable
        }
        delayUntil { putCardsUnderDeckWidget.isFinished }
        action {
            this@GameController.screen.leaveState(showPutCardsUnderDeckActorScreenState)
            cardHand.reattachToOriginalParent()
            val cards = putCardsUnderDeckWidget.complete()
            putCardsAtBottomOfStack(cards)
        }
    }

    /**
     * damages the player
     */
    @AllThreadsAllowed
    fun damagePlayerTimeline(
        damage: Int,
        triggeredByStatusEffect: Boolean = false,
        isPiercing: Boolean = false
    ): Timeline = Timeline.timeline {
        var newDamage: Int? = null
        action {
            newDamage = if (isPiercing) {
                damage
            } else {
                playerStatusEffects.fold(damage) { acc, cur -> cur.modifyDamage(acc) }
            }
        }
        includeLater({ shieldAnimationTimeline() }, { newDamage!! < damage })
        includeLater(
            { Timeline.timeline {
                action {
                    dispatchAnimTimeline(gameRenderPipeline.getScreenShakeTimeline())
                }
                includeAction(GraphicsConfig.damageOverlay(this@GameController.screen))
            } },
            { !triggeredByStatusEffect && newDamage!! > 0 }
        )
        action {
            curPlayerLives -= newDamage!!
            FortyFiveLogger.debug(
                logTag,
                "player got damaged; damage = $newDamage; curPlayerLives = $curPlayerLives"
            )
        }
        includeLater(
            { playerDeathTimeline() },
            { curPlayerLives <= 0 }
        )
        includeLater(
            { executePlayerStatusEffectsAfterDamage(newDamage!!) },
            { !triggeredByStatusEffect && newDamage!! > 0}
        )
    }

    /**
     * adds reserves (plays no animations)
     */
    @AllThreadsAllowed
    fun gainReserves(amount: Int, source: Actor? = null) {
        curReserves += amount
        source?.let {
            dispatchAnimTimeline(reservesGainedAnim(amount, it))
        }
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
        val triggerInformation = TriggerInformation(sourceCard = card, controller = this@GameController)
        include(checkEffectsSingleCard(Trigger.ON_DESTROY, card, triggerInformation))
        include(checkEffectsActiveCards(Trigger.ON_ANY_CARD_DESTROY, triggerInformation))
    }

    fun bounceBullet(card: Card): Timeline = Timeline.timeline {
        action {
            if (card !in revolver.slots.mapNotNull { it.card }) {
                throw RuntimeException("cant bounce card $card because it isn't in the revolver")
            }
            revolver.removeCard(card)
            card.leaveGame()
        }
        include(checkEffectsSingleCard(Trigger.ON_BOUNCE, card))
        include(checkEffectsSingleCard(Trigger.ON_SPECIAL_SELF_DRAWN, card))
        include(checkEffectsSingleCard(Trigger.ON_SPECIAL_SELF_DRAWN_NO_FROM_BOTTOM, card))
        include(tryToPutCardsInHandTimeline(card.name))
    }

    fun putBulletFromRevolverUnderTheDeck(card: Card): Timeline = Timeline.timeline {
        var slot: Int = -1
        action {
            if (card !in revolver.slots.mapNotNull { it.card }) {
                throw RuntimeException("cant put card $card back because it isn't in the revolver")
            }
            slot = revolver.slots.indexOfFirst { it.card === card }
        }
        includeLater(
            { putBulletFromRevolverUnderTheDeck(slot) },
            { true }
        )
    }

    fun putBulletFromRevolverUnderTheDeck(slot: Int): Timeline = Timeline.timeline {
        var card: Card? = null
        action {
            card = revolver.getCardInSlot(slot)
        }
        includeLater(
            { checkEffectsSingleCard(Trigger.ON_LEAVE, card!!) },
            { card != null }
        )
        action {
            @Suppress("NAME_SHADOWING")
            val card = card ?: return@action
            revolver.removeCard(slot)
            card.leaveGame()
            _cardStack.add(card)
        }
    }

    fun putBulletFromHandBackUnderTheDeck(card: Card): Timeline = Timeline.timeline {
        action {
            if (card !in cardHand.cards) return@action
            cardHand.removeCard(card)
            card.isMarked = false
            _cardStack.add(card)
        }
    }

    fun checkEffectsSingleCard(
        trigger: Trigger,
        card: Card,
        triggerInformation: TriggerInformation = TriggerInformation(controller = this)
    ): Timeline {
        FortyFiveLogger.debug(logTag, "checking effects for card $card, trigger $trigger")
        return Timeline.timeline {
            include(card.checkEffects(trigger, triggerInformation, this@GameController))
            trigger
                .cascadeTriggers
                .map { checkEffectsSingleCard(it, card, triggerInformation) }
                .collectTimeline()
                .let { include(it) }
        }
    }

    @MainThreadOnly
    fun checkEffectsActiveCards(
        trigger: Trigger,
        triggerInformation: TriggerInformation = TriggerInformation(controller = this),
        exclude: Card? = triggerInformation.sourceCard
    ): Timeline {
        FortyFiveLogger.debug(logTag, "checking all active cards for trigger $trigger")
        return Timeline.timeline {
            createdCards
                .filter { it.inGame || it.inHand(this@GameController) }
                .filter { it !== exclude }
                .map { it.checkEffects(trigger, triggerInformation, this@GameController) }
                .collectTimeline()
                .let { include(it) }
            trigger
                .cascadeTriggers
                .map { checkEffectsActiveCards(trigger, triggerInformation) }
                .collectTimeline()
                .let { include(it) }
        }
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
        this.screen.enterState(showStatusEffectsState)
    }

    fun putCardFromStackInHandTimeline(card: Card, source: Card? = null): Timeline = Timeline.timeline {
        var skip = false
        action {
            if (card !in _cardStack) {
                FortyFiveLogger.warn(logTag, "could not put card $card from Stack in Hand because it is not in the stack")
                skip = true
                return@action
            }
            _cardStack.remove(card)
            cardHand.addCard(card)
            checkCardMaximums()
            validateCardStack()
            cardOrbAnim(card.actor, reverse = true)
        }
        includeLater(
            { Timeline.timeline {
                include(checkEffectsSingleCard(
                    Trigger.ON_SPECIAL_SELF_DRAWN,
                    card,
                    TriggerInformation(sourceCard = source, controller = this@GameController)
                ))
                include(checkEffectsSingleCard(
                    Trigger.ON_SPECIAL_SELF_DRAWN_NO_FROM_BOTTOM,
                    card,
                    TriggerInformation(sourceCard = source, controller = this@GameController)
                ))
            } },
            { !skip }
        )
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
        var somethingChanged = false
        _playerStatusEffects
            .filter { !it.isStillValid() }
            .forEach {
                somethingChanged = true
                statusEffectDisplay.removeEffect(it)
            }
        _playerStatusEffects.removeIf { !it.isStillValid() }
        if (somethingChanged) {
            if (_playerStatusEffects.isEmpty()) this.screen.leaveState(showStatusEffectsState)
            else this.screen.enterState(showStatusEffectsState)
        }
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
        this.screen.enterState(freezeUIScreenState)
    }

    private fun unfreezeUI() {
        isUIFrozen = false
        FortyFiveLogger.debug(logTag, "unfroze UI")
        cardHand.unfreeze()
        this.screen.leaveState(freezeUIScreenState)
    }

    fun cardRightClicked(card: Card) {
        if (isUIFrozen) return
        if (!card.inGame) return
        val cost = card.rightClickCost ?: return
        if (!cost(cost, card.actor)) return
        appendMainTimeline(checkEffectsSingleCard(Trigger.ON_RIGHT_CLICK, card))
    }

    /**
     * draws a bullet from the stack
     */
    @AllThreadsAllowed
    fun drawCard(fromBottom: Boolean = false): Timeline = Timeline.timeline {
        var card: Card? = null
        action {
            validateCardStack()
            card = (if (!fromBottom) _cardStack.removeFirstOrNull() else _cardStack.removeLastOrNull())
                ?: defaultBullet.create(this@GameController.screen)
            cardHand.addCard(card!!)
            FortyFiveLogger.debug(logTag, "card was drawn; card = $card; cardsToDraw = $cardsToDraw")
            cardsDrawn++
        }
        includeLater(
            { checkEffectsSingleCard(Trigger.ON_SPECIAL_SELF_DRAWN, card!!) },
            { fromBottom }
        )
    }

    fun putCardAtBottomOfStack(card: Card) {
        _cardStack.add(card)
        validateCardStack()
    }

    fun putCardsAtBottomOfStack(cards: List<Card>) {
        _cardStack.addAll(cards)
        validateCardStack()
    }

    private fun validateCardStack() {
        var index = 0
        while (index < _cardStack.size) {
            val card = _cardStack[index]
            if (card.isAlwaysAtBottom) {
                _cardStack.removeAt(index)
                _cardStack.add(card)
            }
            index++
        }
        index = _cardStack.size - 1
        while (index >= 0) {
            if (_cardStack.take(index + 1).all { it.isAlwaysAtTop }) break
            val card = _cardStack[index]
            if (card.isAlwaysAtTop) {
                _cardStack.removeAt(index)
                _cardStack.add(0, card)
                continue
            }
            index--
        }
    }

    fun cost(cost: Int, animTarget: Actor? = null): Boolean {
        if (cost > curReserves) return false
        curReserves -= cost
        SaveState.usedReserves += cost
        reservesSpent += cost
        FortyFiveLogger.debug(logTag, "$cost reserves were spent, curReserves = $curReserves")
        if (animTarget == null) return true
        dispatchAnimTimeline(Timeline.timeline {
            includeLater(
                { reservesPaidAnim(cost, animTarget) },
                { true }
            )
        })
        return true
    }

    private fun reservesPaidAnim(amount: Int, animTarget: Actor): Timeline = Timeline.timeline {
        repeat(amount) {
            action {
                SoundPlayer.situation("orb_anim_playing", this@GameController.screen)
                gameRenderPipeline.addOrbAnimation(GraphicsConfig.orbAnimation(
                    stageCoordsOfReservesIcon(),
                    animTarget.localToStageCoordinates(Vector2(0f, 0f)) +
                            Vector2(animTarget.width / 2, animTarget.height / 2),
                    true,
                    gameRenderPipeline
                ))
            }
            delay(50)
        }
    }

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline = Timeline.timeline {
        repeat(amount) {
            action {
                SoundPlayer.situation("orb_anim_playing", this@GameController.screen)
                gameRenderPipeline.addOrbAnimation(GraphicsConfig.orbAnimation(
                    animSource.localToStageCoordinates(Vector2(0f, 0f)) +
                            Vector2(animSource.width / 2, animSource.height / 2),
                    stageCoordsOfReservesIcon(),
                    true,
                    gameRenderPipeline
                ))
            }
            delay(50)
        }
    }

    private fun stageCoordsOfReservesIcon(): Vector2 = this.screen.centeredStageCoordsOfActor("reserves_icon")

    override fun end() {
        createdCards.forEach { it.dispose() }
        gameDirector.end()
        FortyFiveLogger.title("game ends")
        FortyFive.currentGame = null
    }

    /**
     * called when an enemy was defeated
     */
    @MainThreadOnly
    fun enemyDefeated(enemy: Enemy) {
        enemy.onDefeat()
        enemyArea.onEnemyDefeated()
        SaveState.enemiesDefeated++
        if (enemyArea.enemies.all { it.isDefeated }) hasWon = true
        FortyFiveLogger.debug(logTag, "player won")
    }

    @MainThreadOnly
    private fun completeWin() {
        val money = -enemyArea.enemies.sumOf { it.currentHealth }
        val playerGetsCard = !gameDirector.encounter.special && Utils.coinFlip(rewardChance)
        appendMainTimeline(Timeline.timeline {
            action {
                SoundPlayer.changeMusicTo(SoundPlayer.Theme.MAIN, 5_000)
                SaveState.encountersWon++
                this@GameController.screen.enterState(showWinScreen)
                if (money > 0) this@GameController.screen.enterState(showCashItem)
                TemplateString.updateGlobalParam("game.overkillCash", money)
                if (playerGetsCard) this@GameController.screen.enterState(showCardItem)
            }
            delayUntil { popupEvent != null }
            action {
                val start = this@GameController.screen.centeredStageCoordsOfActor("win_screen_cash_symbol")
                val end = this@GameController.screen.centeredStageCoordsOfActor("cash_symbol")
                if (money > 0) {
                    SoundPlayer.situation("orb_anim_playing", this@GameController.screen)
                    gameRenderPipeline.addOrbAnimation(RenderPipeline.OrbAnimation(
                        orbTexture = "cash_symbol",
                        width = 30f,
                        height = 30f,
                        duration = 600,
                        segments = 20,
                        renderPipeline = gameRenderPipeline,
                        position = RenderPipeline.OrbAnimation.curvedPath(start, end)
                    ))
                    SoundPlayer.situation("money_earned", this@GameController.screen)
                }
            }
            delay(if (money > 0) 600 else 0)
            action {
                SaveState.earnMoney(money)
            }
            delay(300)
            action {
                popupEvent = null
                encounterContext.completed()
                SaveState.write()

                val chooseCardContext = object : ChooseCardScreenContext {
                    override val forwardToScreen: String = encounterContext.forwardToScreen
                    override var seed: Long = TimeUtils.millis()
                    override val nbrOfCards: Int = 3
                    override val types: List<String> = listOf()
                    override val enableRerolls: Boolean = true
                    override var amountOfRerolls: Int = 0
                    override val rerollPriceIncrease: Int = rewardRerollPriceIncrease
                    override val rerollBasePrice: Int = rewardRerollBasePrice

                    override fun completed() { }
                }

                if (playerGetsCard) {
                    MapManager.changeToChooseCardScreen(chooseCardContext)
                } else {
                    FortyFive.changeToScreen(ConfigFileManager.screenBuilderFor(encounterContext.forwardToScreen))
                }

            }
        })
    }

    @MainThreadOnly
    fun playerDeathTimeline(): Timeline = Timeline.timeline {
        action {
            FortyFiveLogger.debug(logTag, "player lost")
            playerLost = true
        }
        include(gameRenderPipeline.getFadeToBlackTimeline(2000, stayBlack = true))
        delay(500)
        action {
            mainTimeline.stopTimeline()
            animTimelines.forEach(Timeline::stopTimeline)
            FortyFive.newRun(true)
        }
    }

    sealed class RevolverRotation {

        abstract val amount: Int

        abstract val directionString: String

        class Right(override val amount: Int) : RevolverRotation() {

            override val directionString: String = "right"

            override fun withAmount(amount: Int): RevolverRotation = Right(amount)

            override fun toString(): String = "Right($amount)"
        }
        class Left(override val amount: Int) : RevolverRotation() {

            override val directionString: String = "left"

            override fun withAmount(amount: Int): RevolverRotation = Left(amount)

            override fun toString(): String = "Left($amount)"
        }
        object None : RevolverRotation() {

            override val amount: Int = 0

            override val directionString: String = "none"

            override fun withAmount(amount: Int): RevolverRotation = None

            override fun toString(): String = "None"
        }

        abstract fun withAmount(amount: Int): RevolverRotation

        companion object {

            fun fromOnj(onj: OnjNamedObject): RevolverRotation = when (onj.name) {
                "Right" -> Right(onj.get<Long>("amount").toInt())
                "Left" -> Left(onj.get<Long>("amount").toInt())
                "Dont" -> None
                else -> throw RuntimeException("unknown revolver rotation: ${onj.name}")
            }

        }

    }

    interface EncounterContext {

        val encounterIndex: Int

        val forwardToScreen: String

        val forceCards: List<String>?
            get() = null

        fun completed()
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
        const val showTutorialActorScreenState = "showTutorial"
        const val showWinScreen = "showWinScreen"
        const val showCashItem = "showCashItem"
        const val showCardItem = "showCardItem"
        const val showSelectionPopup = "showSelectionPopup"
        const val showStatusEffectsState = "playerHasStatusEffects"

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
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
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.gameComponents.*
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import ktx.actors.onClick
import onj.parser.OnjParser
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
    private val encounterModifierDisplayTemplateName = onj.get<String>("encounterModifierDisplayTemplateName")
    private val encounterModifierParentName = onj.get<String>("encounterModifierParentName")
    private val tutorialInfoActorName = onj.get<String>("tutorialInfoActorName")
    private val musicAfterWin = onj.get<String>("musicAfterWin")
    private val musicBeforeWin = onj.get<String>("musicBeforeWin")
    private val musicTransitionTime = onj.get<Long>("musicTransitionTime").toInt()

    val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()

    val baseReserves by templateParam(
        "game.baseReserves", onj.get<Long>("reservesAtRoundBegin").toInt()
    )

    val softMaxCards = onj.get<Long>("softMaxCards").toInt()
    val hardMaxCards = onj.get<Long>("hardMaxCards").toInt()

    private val rewardChance = onj.get<Double>("rewardChance").toFloat()

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
    lateinit var tutorialInfoActor: TutorialInfoActor
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
    override fun init(onjScreen: OnjScreen, context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        encounterContext = context
        curScreen = onjScreen
        FortyFive.currentGame = this
        gameRenderPipeline = GameRenderPipeline(onjScreen)
        FortyFive.useRenderPipeline(gameRenderPipeline)
        onjScreen.background = GraphicsConfig.encounterBackgroundFor(MapManager.currentDetailMap.biome)
        FortyFiveLogger.title("game starting")

        warningParent = onjScreen.namedActorOrError(warningParentName) as? CustomWarningParent
            ?: throw RuntimeException("actor named $warningParentName must be of type CustomWarningParent")
        statusEffectDisplay = onjScreen.namedActorOrError(statusEffectDisplayName) as? StatusEffectDisplay
            ?: throw RuntimeException("actor named $statusEffectDisplayName must be of type StatusEffectDisplay")
        tutorialInfoActor = onjScreen.namedActorOrError(tutorialInfoActorName) as? TutorialInfoActor
            ?: throw RuntimeException("actor named $tutorialInfoActorName must be of type TutorialInfoActor")

        gameDirector.init()

        initCards()
        initCardHand()
        initRevolver()
        initCardSelector()
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
                    .mapNotNull { it.executeOnPlayerTurnStart() }
                    .collectTimeline()
                    .let { include(it) }
        })
        onjScreen.invalidateEverything()
        gameDirector.chooseEnemyActions()
        SoundPlayer.transitionToMusic(musicBeforeWin, musicTransitionTime, curScreen)
    }

    private fun initCards() {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cards = gameDirector.encounter.forceCards ?: SaveState.curDeck.cards

        val cardsArray = onj.get<OnjArray>("cards")

        cardPrototypes = Card
            .getFrom(cardsArray, ::initCard)
            .toMutableList()

        cards.forEach { cardName ->
            val card = cardPrototypes.find { it.name == cardName } ?: throw  RuntimeException("unknown card $cardName")
            curScreen.borrowResource("${Card.cardTexturePrefix}$cardName")
            card
                .forceLoadCards
                .forEach { curScreen.borrowResource("${Card.cardTexturePrefix}$it") }
        }
        onj
            .get<OnjArray>("alwaysLoadCards")
            .value
            .forEach { curScreen.borrowResource("${Card.cardTexturePrefix}${it.value as String}") }

        cards.forEach { cardName ->
            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            cardStack.add(card.create(curScreen))
        }

        if (gameDirector.encounter.shuffleCards) cardStack.shuffle()
        validateCardStack()

        FortyFiveLogger.debug(logTag, "card stack: $cardStack")

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

        _remainingCards = cardStack.size

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
        curScreen.enterState(showTutorialActorScreenState)
        (curScreen.namedActorOrError("tutorial_info_text") as AdvancedTextWidget).setRawText(tutorialTextPart.text, listOf())
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
        curScreen.leaveState(showTutorialActorScreenState)
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

    fun initEnemyArea(enemies: List<Enemy>) {
        val curScreen = curScreen

        val enemyAreaName = enemyAreaOnj.get<String>("actorName")
        val enemyArea = curScreen.namedActorOrError(enemyAreaName)
        if (enemyArea !is EnemyArea) throw RuntimeException("actor named $enemyAreaName must be a EnemyArea")

        enemies.forEach { enemy ->
            enemyArea.addEnemy(enemy)
            enemy.actor.onClick { enemyArea.selectEnemy(enemy) }
        }

        if (enemies.isEmpty()) throw RuntimeException("enemyArea must have at least one enemy")

        this.enemyArea = enemyArea
    }

    fun addEncounterModifier(modifier: EncounterModifier) {
        _encounterModifiers.add(null to modifier)
        val parent = (curScreen.namedActorOrError(encounterModifierParentName) as? FlexBox
                ?: throw RuntimeException("actor named $encounterModifierParentName must be a FlexBox"))
        curScreen.screenBuilder.generateFromTemplate(
            encounterModifierDisplayTemplateName,
            mapOf(
                "symbol" to OnjString(GraphicsConfig.encounterModifierIcon(modifier)),
                "modifierName" to OnjString(GraphicsConfig.encounterModifierDisplayName(modifier)),
                "modifierDescription" to OnjString(GraphicsConfig.encounterModifierDescription(modifier)),
            ),
            parent,
            curScreen
        )!!
        parent.invalidateHierarchy()
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
                !cost(card.cost, card.actor)
            ) {
                SoundPlayer.situation("not_allowed", curScreen)
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
        includeLater(
            { checkEffectsSingleCard(Trigger.ON_ENTER, card) },
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
            revolver
                .slots
                .mapNotNull { it.card }
                .filter { it !== exclude }
                .forEach { it.actor.enterSelectionMode() }
            TemplateString.updateGlobalParam("game.revolverPopupText", text)
            curScreen.enterState(showSelectionPopup)
            selectedCard = null
        }
        delayUntil { selectedCard != null }
        action {
            revolver
                .slots
                .mapNotNull { it.card }
                .forEach { it.actor.exitSelectionMode() }
            store("selectedCard", selectedCard!!)
            curScreen.leaveState(showSelectionPopup)
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
            if (remainingCardsToDraw != 0) curScreen.enterState(cardDrawActorScreenState)
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
                    SoundPlayer.situation("card_drawn", curScreen)
                    popupEvent = null
                    drawCard(fromBottom)
                    TemplateString.updateGlobalParam(
                        "game.drawCardText",
                        "draw ${(remainingCardsToDraw - cur - 1) pluralS "card"} ${if (fromBottom) "from the bottom of your deck" else ""}"
                    )
                }
            }
        }}, { remainingCardsToDraw != 0 })
        action {
            if (remainingCardsToDraw == 0) return@action
            curScreen.leaveState(cardDrawActorScreenState)
            checkCardMaximums()
        }
        val cardsDrawnTriggerInfo = TriggerInformation(multiplier = remainingCardsToDraw, amountOfCardsDrawn = remainingCardsToDraw)
        val oneOrMoreDrawnTriggerInfo = TriggerInformation(amountOfCardsDrawn = remainingCardsToDraw)
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

    fun enemyAttackTimeline(damage: Int): Timeline = Timeline.timeline {
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
            curScreen.enterState(showEnemyAttackPopupScreenState)
            FortyFiveLogger.debug(logTag, "enemy attacking: damage = $damage; parryCard = $parryCard")
            SoundPlayer.situation("enter_parry", curScreen)
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
                    { checkEffectsSingleCard(Trigger.ON_LEAVE, parryCard, TriggerInformation(isOnShot = true)) },
                    { parryCard.shouldRemoveAfterShot(this@GameController) }
                )
                action {
                    curScreen.leaveState(showEnemyAttackPopupScreenState)
                    gameRenderPipeline.stopParryEffect()
                    if (parryCard.shouldRemoveAfterShot(this@GameController)) {
                        if (!parryCard.isUndead) {
                            SoundPlayer.situation("orb_anim_playing", curScreen)
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
                        SoundPlayer.situation("enemy_attack", curScreen)
                    }
                    includeLater(
                        { damagePlayerTimeline(remainingDamage!!) },
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
                    curScreen.leaveState(showEnemyAttackPopupScreenState)
                    gameRenderPipeline.stopParryEffect()
                    FortyFiveLogger.debug(logTag, "Player didn't parry")
                    SoundPlayer.situation("enemy_attack", curScreen)
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
                cardHand.addCard(cardProto.create(curScreen))
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
        } else if (permanentWarningId != null) {
            warningParent.removePermanentWarning(permanentWarningId)
        }
    }

    /**
     * shoots the revolver
     */
    @MainThreadOnly
    fun shoot() {
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
                SoundPlayer.situation("revolver_shot", curScreen)
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
                val triggerInformation = TriggerInformation(targetedEnemies = targetedEnemies, isOnShot = true)
                include(checkEffectsSingleCard(Trigger.ON_SHOT, cardToShoot, triggerInformation))
                includeLater(
                    { checkEffectsSingleCard(Trigger.ON_LEAVE, cardToShoot, triggerInformation) },
                    { cardToShoot.shouldRemoveAfterShot(this@GameController) }
                )
                action {
                    if (cardToShoot.shouldRemoveAfterShot(this@GameController)) {
                        if (!cardToShoot.isUndead) {
                            putCardAtBottomOfStack(cardToShoot)
                            SoundPlayer.situation("orb_anim_playing", curScreen)
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

    private fun cardOrbAnim(actor: Actor) = GraphicsConfig.orbAnimation(
        actor.localToStageCoordinates(Vector2(0f, 0f)) +
                Vector2(actor.width / 2, actor.height / 2),
        curScreen.centeredStageCoordsOfActor("deck_icon"),
        false
    )

    fun tryApplyStatusEffectToEnemy(statusEffect: StatusEffect, enemy: Enemy): Timeline = Timeline.timeline {
        if (encounterModifiers.any { !it.shouldApplyStatusEffects() }) return Timeline()
        action {
            enemy.applyEffect(statusEffect)
        }
    }

    fun rotateRevolver(rotation: RevolverRotation): Timeline = Timeline.timeline {
        var newRotation = modifiers(rotation) { modifier, cur -> modifier.modifyRevolverRotation(cur) }
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
            val info = TriggerInformation(multiplier = newRotation.amount)
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
                SoundPlayer.situation("end_turn", curScreen)
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
            include(bannerAnimationTimeline("enemy_turn_banner"))
            include(gameDirector.checkActions())
            include(executePlayerStatusEffectsOnNewTurn())
            action {
                gameDirector.chooseEnemyActions()
                SoundPlayer.situation("turn_begin", curScreen)
            }
            include(bannerAnimationTimeline("player_turn_banner"))
            action {
                curReserves = baseReserves
            }
            include(drawCardPopupTimeline(cardsToDraw, false))
            encounterModifiers
                    .mapNotNull { it.executeOnPlayerTurnStart() }
                    .collectTimeline()
                    .let { include(it) }
            includeLater({ checkStatusEffectsAfterTurn() }, { true })
            includeLater({ checkEffectsActiveCards(Trigger.ON_ROUND_START) }, { true })
        })
    }

    private fun bannerAnimationTimeline(drawableHandle: ResourceHandle): Timeline = BannerAnimation(
        ResourceManager.get(curScreen, drawableHandle),
        curScreen,
        1_500,
        500,
        1.4f,
        1.1f
    ).asTimeline(this)

    private fun shieldAnimationTimeline(): Timeline = Timeline.timeline {
        val bannerAnim = BannerAnimation(
            ResourceManager.get(curScreen, "shield_icon_large"),
            curScreen,
            1_000,
            150,
            0.3f,
            0.5f,
            interpolation = Interpolation.pow2In,
            customShader = ResourceManager.get<BetterShader>(curScreen, "glow_shader_shield")
        ).asTimeline(this@GameController).asAction()
        val postProcessorAction = Timeline.timeline {
            delay(100)
            include(gameRenderPipeline.getScreenShakePopoutTimeline())
            delay(50)
            action { SoundPlayer.situation("shield_anim", curScreen) }
        }.asAction()
        parallelActions(bannerAnim, postProcessorAction)
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
            putCardsAtBottomOfStack(cards)
        }
    }

    /**
     * damages the player
     */
    @AllThreadsAllowed
    fun damagePlayerTimeline(damage: Int, triggeredByStatusEffect: Boolean = false): Timeline = Timeline.timeline {
        var newDamage: Int? = null
        action {
            newDamage = playerStatusEffects.fold(damage) { acc, cur -> cur.modifyDamage(acc) }
        }
        includeLater({ shieldAnimationTimeline() }, { newDamage!! < damage })
        includeLater(
            { Timeline.timeline {
                action {
                    dispatchAnimTimeline(gameRenderPipeline.getScreenShakeTimeline())
                }
                includeAction(GraphicsConfig.damageOverlay(curScreen))
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
        val triggerInformation = TriggerInformation(sourceCard = card)
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
        include(tryToPutCardsInHandTimeline(card.name))
    }

    fun checkEffectsSingleCard(
        trigger: Trigger,
        card: Card,
        triggerInformation: TriggerInformation = TriggerInformation()
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
        triggerInformation: TriggerInformation = TriggerInformation()
    ): Timeline {
        FortyFiveLogger.debug(logTag, "checking all active cards for trigger $trigger")
        return Timeline.timeline {
            createdCards
                .filter { it.inGame || it.inHand(this@GameController) }
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
        curScreen.enterState(showStatusEffectsState)
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
            if (_playerStatusEffects.isEmpty()) curScreen.leaveState(showStatusEffectsState)
            else curScreen.enterState(showStatusEffectsState)
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
    fun drawCard(fromBottom: Boolean = false) {
        validateCardStack()
        val card = (if (!fromBottom) cardStack.removeFirstOrNull() else cardStack.removeLastOrNull())
            ?: defaultBullet.create(curScreen)
        cardHand.addCard(card)
        FortyFiveLogger.debug(logTag, "card was drawn; card = $card; cardsToDraw = $cardsToDraw")
        cardsDrawn++
    }

    fun putCardAtBottomOfStack(card: Card) {
        cardStack.add(card)
        validateCardStack()
    }

    fun putCardsAtBottomOfStack(cards: List<Card>) {
        cardStack.addAll(cards)
        validateCardStack()
    }

    private fun validateCardStack() {
        var index = 0
        while (index < cardStack.size) {
            val card = cardStack[index]
            if (card.isAlwaysAtBottom) {
                cardStack.removeAt(index)
                cardStack.add(card)
            }
            index++
        }
        index = cardStack.size - 1
        while (index >= 0) {
            val card = cardStack[index]
            if (card.isAlwaysAtTop) {
                cardStack.removeAt(index)
                cardStack.add(0, card)
                continue
            }
            index--
        }
    }

    private fun cost(cost: Int, animTarget: Actor? = null): Boolean {
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
                SoundPlayer.situation("orb_anim_playing", curScreen)
                gameRenderPipeline.addOrbAnimation(GraphicsConfig.orbAnimation(
                    stageCoordsOfReservesIcon(),
                    animTarget.localToStageCoordinates(Vector2(0f, 0f)) +
                            Vector2(animTarget.width / 2, animTarget.height / 2),
                    true
                ))
            }
            delay(50)
        }
    }

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline = Timeline.timeline {
        repeat(amount) {
            action {
                SoundPlayer.situation("orb_anim_playing", curScreen)
                gameRenderPipeline.addOrbAnimation(GraphicsConfig.orbAnimation(
                    animSource.localToStageCoordinates(Vector2(0f, 0f)) +
                            Vector2(animSource.width / 2, animSource.height / 2),
                    stageCoordsOfReservesIcon(),
                    true
                ))
            }
            delay(50)
        }
    }

    private fun stageCoordsOfReservesIcon(): Vector2 = curScreen.centeredStageCoordsOfActor("reserves_icon")

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
                SoundPlayer.transitionToMusic(musicAfterWin, musicTransitionTime, curScreen)
                SaveState.encountersWon++
                curScreen.enterState(showWinScreen)
                if (money > 0) curScreen.enterState(showCashItem)
                TemplateString.updateGlobalParam("game.overkillCash", money)
                if (playerGetsCard) curScreen.enterState(showCardItem)
            }
            delayUntil { popupEvent != null }
            action {
                val start = curScreen.centeredStageCoordsOfActor("win_screen_cash_symbol")
                val end = curScreen.centeredStageCoordsOfActor("cash_symbol")
                if (money > 0) {
                    SoundPlayer.situation("orb_anim_playing", curScreen)
                    gameRenderPipeline.addOrbAnimation(RenderPipeline.OrbAnimation(
                        orbTexture = "cash_symbol",
                        width = 30f,
                        height = 30f,
                        duration = 600,
                        segments = 20,
                        position = RenderPipeline.OrbAnimation.curvedPath(start, end)
                    ))
                    SoundPlayer.situation("money_earned", curScreen)
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
                    override val seed: Long = TimeUtils.millis()
                    override val nbrOfCards: Int = 3
                    override val types: List<String> = listOf()
                    override fun completed() { }
                }

                if (playerGetsCard) {
                    MapManager.changeToChooseCardScreen(chooseCardContext)
                } else {
                    FortyFive.changeToScreen(encounterContext.forwardToScreen)
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

            override fun toString(): String = "Right($amount)"
        }
        class Left(override val amount: Int) : RevolverRotation() {

            override val directionString: String = "left"

            override fun toString(): String = "Left($amount)"
        }
        object None : RevolverRotation() {

            override val amount: Int = 0

            override val directionString: String = "none"

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

    interface EncounterContext {

        val encounterIndex: Int

        val forwardToScreen: String

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

package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.game.card.CardPrototype
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.map.detailMap.EncounterMapEvent
import com.fourinachamber.fourtyfive.rendering.GameRenderPipeline
import com.fourinachamber.fourtyfive.screen.gameComponents.CardHand
import com.fourinachamber.fourtyfive.screen.gameComponents.CoverArea
import com.fourinachamber.fourtyfive.screen.gameComponents.EnemyArea
import com.fourinachamber.fourtyfive.screen.gameComponents.Revolver
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.*
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

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private val enemyAreaOnj = onj.get<OnjObject>("enemyArea")
    private val coverAreaOnj = onj.get<OnjObject>("coverArea")
    private val enemiesOnj = onj.get<OnjArray>("enemies")
    private val cardDrawActorName = onj.get<String>("cardDrawActor")
    private val destroyCardInstructionActorName = onj.get<String>("destroyCardInstructionActor")
    private val playerLivesLabelName = onj.get<String>("playerLivesLabelName")
    private val endTurnButtonName = onj.get<String>("endTurnButtonName")
    private val shootButtonName = onj.get<String>("shootButtonName")
    private val reservesLabelName = onj.get<String>("reservesLabelName")

    private val winScreen = onj.get<String>("winScreen")
    private val looseScreen = onj.get<String>("looseScreen")

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
    lateinit var coverArea: CoverArea
        private set
    lateinit var cardDrawActor: Actor
        private set
    lateinit var destroyCardInstructionActor: Actor
        private set
    lateinit var shootButton: Actor
        private set
    lateinit var endTurnButton: Actor
        private set
    lateinit var playerLivesLabel: CustomLabel
        private set
    lateinit var reservesLabel: CustomLabel
        private set

    private var cardPrototypes: List<CardPrototype> = listOf()
    val createdCards: MutableList<Card> = mutableListOf()
    private var bulletStack: MutableList<Card> = mutableListOf()
    private var coverCardStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var remainingBullets: Int by multipleTemplateParam(
        "game.remainingBullets", bulletStack.size,
        "game.remainingBulletsPluralS" to { if (it == 1) "" else "s" }
    )

    private var remainingCovers: Int by multipleTemplateParam(
        "game.remainingCovers", coverCardStack.size,
        "game.remainingCoversPluralS" to { if (it == 1) "" else "s" }
    )

//    var enemies: List<Enemy> = listOf()

    var curPlayerLives: Int
        set(value) {
            val newLives = max(value, 0)
            SaveState.playerLives = newLives
        }
        get() = SaveState.playerLives

    @Suppress("unused")
    val playerLivesAtStart: Int by templateParam("game.basePlayerLives", SaveState.playerLives)

    private val timeline: Timeline = Timeline(mutableListOf()).apply {
        start()
    }

    private var isUIFrozen: Boolean = false

    /**
     * the current phase of the game
     */
    var currentState: GameState = GameState.Free
        private set

    /**
     * counts up every round; starts at 0
     */
    var roundCounter: Int = 0
        private set(value) {
            field = value
            FourtyFiveLogger.title("round: $value")
        }

    /**
     * counts up every revolver turn; starts at 0
     */
    var turnCounter: Int = 0
        private set

    var curReserves: Int by templateParam("game.curReserves", 0)

    private var curGameAnims: MutableList<GameAnimation> = mutableListOf()

    private lateinit var defaultBullet: CardPrototype
    private lateinit var defaultCover: CardPrototype

    lateinit var gameRenderPipeline: GameRenderPipeline
    private lateinit var encounterMapEvent: EncounterMapEvent

    var modifier: EncounterModifier? = null

    @MainThreadOnly
    override fun init(onjScreen: OnjScreen, context: Any?) {

//        if (context !is EncounterMapEvent) { // TODO: comment back in
//            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
//        }
//        encounterMapEvent = context
//        modifier = EncounterModifier.BewitchedMist // TODO: remove
        SaveState.read()
        curScreen = onjScreen
        FourtyFive.currentGame = this
        gameRenderPipeline = GameRenderPipeline(onjScreen)
        FourtyFive.useRenderPipeline(gameRenderPipeline)

        FourtyFiveLogger.title("game starting")

        initCards()

        cardDrawActor = onjScreen.namedActorOrError(cardDrawActorName)

        destroyCardInstructionActor = onjScreen.namedActorOrError(destroyCardInstructionActorName)
        destroyCardInstructionActor.isVisible = false

        initButtons()
        initCardHand()
        initLabels()
        initRevolver()
        initEnemyArea()
        initCoverArea()

        changeState(GameState.InitialDraw(cardsToDrawInFirstRound))
        onjScreen.invalidateEverything()
    }

    private fun initCards() {
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        cardPrototypes = Card
            .getFrom(onj.get<OnjArray>("cards"), curScreen, ::initCard)
            .toMutableList()

        val startDeck: MutableList<Card> = mutableListOf()
        onj.get<OnjArray>("startDeck").value.forEach { entry ->

            entry as OnjObject
            val entryName = entry.get<String>("name")

            val card = cardPrototypes.firstOrNull { it.name == entryName }
                ?: throw RuntimeException("unknown card name is start deck: $entryName")

            repeat(entry.get<Long>("amount").toInt()) {
                startDeck.add(card.create())
            }
        }

        bulletStack = startDeck.filter { it.type == Card.Type.BULLET }.toMutableList()
        remainingBullets = bulletStack.size
        coverCardStack = startDeck.filter { it.type == Card.Type.COVER }.toMutableList()
        remainingCovers = coverCardStack.size

        SaveState.additionalCards.forEach { entry ->
            val (cardName, amount) = entry

            val card = cardPrototypes.firstOrNull { it.name == cardName }
                ?: throw RuntimeException("unknown card name in saveState: $cardName")

            repeat(amount) {
                if (card.type == Card.Type.BULLET) bulletStack.add(card.create())
                else coverCardStack.add(card.create())
            }
        }

        bulletStack.shuffle()
        coverCardStack.shuffle()

        FourtyFiveLogger.debug(logTag, "bullet stack: $bulletStack")
        FourtyFiveLogger.debug(logTag, "cover stack: $coverCardStack")

        val defaultBulletName = onj.get<String>("defaultBullet")
        val defaultCoverName = onj.get<String>("defaultCover")

        defaultBullet = cardPrototypes
            .filter { it.type == Card.Type.BULLET }
            .firstOrNull { it.name == defaultBulletName }
            ?: throw RuntimeException("unknown default bullet: $defaultBulletName")

        defaultCover = cardPrototypes
            .filter { it.type == Card.Type.COVER }
            .firstOrNull { it.name == defaultCoverName }
            ?: throw RuntimeException("unknown default cover: $defaultBulletName")

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

    @MainThreadOnly
    fun changeState(next: GameState) {
        if (next == currentState) return
        FourtyFiveLogger.debug(logTag, "changing state from $currentState to $next")
        currentState.transitionAway(this)
        currentState = next
        currentState.transitionTo(this)
        if (next.shouldIncrementRoundCounter()) roundCounter++
    }

    private var updateCount = 0 //TODO: this is stupid

    @MainThreadOnly
    override fun update() {

        if (updateCount == 3) curScreen.invalidateEverything() //TODO: this is stupid
        updateCount++

        timeline.update()

        if (timeline.isFinished && isUIFrozen) unfreezeUI()
        if (!timeline.isFinished && !isUIFrozen) freezeUI()
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
        FourtyFiveLogger.debug(logTag, "playing game animation: $anim")
        anim.start()
        curGameAnims.add(anim)
    }

    /**
     * changes the game to the SpecialDraw phase and sets the amount of cards to draw to [amount]
     */
    @AllThreadsAllowed
    fun specialDraw(amount: Int) {
        if (currentState !is GameState.Free) return
        changeState(GameState.SpecialDraw(amount))
    }

    private fun initButtons() {
        shootButton = curScreen.namedActorOrError(shootButtonName)
        endTurnButton = curScreen.namedActorOrError(endTurnButtonName)
    }

    private fun initCardHand() {
        val curScreen = curScreen
        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActorOrError(cardHandName)
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        this.cardHand = cardHand
    }

    private fun initLabels() {
        val curScreen = curScreen
        val playerLives = curScreen.namedActorOrError(playerLivesLabelName)
        if (playerLives !is CustomLabel) throw RuntimeException("actor named $playerLivesLabelName must be a Label")
        playerLivesLabel = playerLives
        val reserves = curScreen.namedActorOrError(reservesLabelName)
        if (reserves !is CustomLabel) throw RuntimeException("actor named $reservesLabelName must be a Label")
        reservesLabel = reserves
    }

    private fun initCoverArea() {
        val curScreen = curScreen
        val coverAreaName = coverAreaOnj.get<String>("actorName")
        val coverArea = curScreen.namedActorOrError(coverAreaName)
        if (coverArea !is CoverArea) throw RuntimeException("actor named $coverAreaName must be a CoverArea")
        this.coverArea = coverArea
        val dropOnj = coverAreaOnj.get<OnjNamedObject>("dropBehaviour")
        coverArea.slotDropConfig = cardDragAndDrop to dropOnj
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

    private fun initEnemyArea() {
        val curScreen = curScreen

        val enemyAreaName = enemyAreaOnj.get<String>("actorName")
        val enemyArea = curScreen.namedActorOrError(enemyAreaName)
        if (enemyArea !is EnemyArea) throw RuntimeException("actor named $enemyAreaName must be a EnemyArea")

        val enemies = Enemy.getFrom(enemiesOnj, enemyArea, curScreen)

        enemyAreaOnj
            .get<OnjArray>("enemies")
            .value
            .forEach { nameOnj ->
                val name = nameOnj.value as String
                enemyArea.addEnemy(
                    enemies.firstOrNull { it.name == name} ?: throw RuntimeException("no enemy with name $name")
                )
            }

        if (enemyArea.enemies.isEmpty()) throw RuntimeException("enemyArea must have at leas one enemy")
        if (enemyArea.enemies.size != 1) enemyArea.selectedEnemy = enemyArea.enemies[0]

        this.enemyArea = enemyArea
    }

    /**
     * puts [card] in [slot] of the revolver (checks if the card is a bullet)
     */
    @MainThreadOnly
    fun loadBulletInRevolver(card: Card, slot: Int) {
        if (card.type != Card.Type.BULLET || !card.allowsEnteringGame()) return
        if (revolver.getCardInSlot(slot) != null) return
        if (!cost(card.cost)) return
        cardHand.removeCard(card)
        revolver.setCard(slot, card)
        FourtyFiveLogger.debug(logTag, "card $card entered revolver in slot $slot")
        card.onEnter()
        checkEffectsSingleCard(Trigger.ON_ENTER, card)
    }

    /**
     * adds a new cover to a slot in the cover area (checks if the card is a cover)
     */
    @MainThreadOnly
    fun addCover(card: Card, slot: Int) {
        if (card.type != Card.Type.COVER || !card.allowsEnteringGame()) return
        if (!coverArea.acceptsCover(slot, roundCounter) || !cost(card.cost)) return
        coverArea.addCover(card, slot, roundCounter)
        cardHand.removeCard(card)
        FourtyFiveLogger.debug(logTag, "cover $card was placed in slot $slot")
        card.onEnter()
        checkEffectsSingleCard(Trigger.ON_ENTER, card)
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
        FourtyFiveLogger.debug(logTag, "card $name entered hand")
    }

    /**
     * shoots the revolver
     */
    @MainThreadOnly
    fun shoot() {
        if (!currentState.allowsShooting()) return
        turnCounter++

        val cardToShoot = revolver.getCardInSlot(5)
        var rotationDirection = cardToShoot?.rotationDirection ?: RevolverRotation.RIGHT
        if (modifier != null) rotationDirection = modifier!!.modifyRevolverRotation(rotationDirection)
        val enemy = enemyArea.getTargetedEnemy()

        FourtyFiveLogger.debug(logTag, "revolver is shooting; turn = $turnCounter; cardToShoot = $cardToShoot")

        var enemyDamageTimeline: Timeline? = null
        var damageStatusEffectTimeline: Timeline? = null
        var turnStatusEffectTimeline: Timeline? = null
        var effectTimeline: Timeline? = null

        if (cardToShoot != null) {

            enemyDamageTimeline = Timeline.timeline {
                action {
                    if (cardToShoot.shouldRemoveAfterShot) revolver.removeCard(5)
                }
                include(enemy.damage(cardToShoot.curDamage))
                action { cardToShoot.afterShot() }
            }

            damageStatusEffectTimeline =
                enemy.executeStatusEffectsAfterDamage(cardToShoot.curDamage)
            turnStatusEffectTimeline = enemy.executeStatusEffectsAfterRevolverTurn()

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

        val damagePlayerTimeline = enemy.damagePlayer(shotEmptyDamage, this@GameController)

        val timeline = Timeline.timeline {

            includeLater(
                { damagePlayerTimeline },
                { cardToShoot == null }
            )

            enemyDamageTimeline?.let { include(it) }

            includeLater(
                { damageStatusEffectTimeline!! },
                { enemy.currentLives > 0 && damageStatusEffectTimeline != null }
            )
            includeLater(
                { effectTimeline!! },
                { enemy.currentLives > 0 && effectTimeline != null }
            )

            action {
                when (rotationDirection) {
                    RevolverRotation.LEFT -> revolver.rotateLeft()
                    RevolverRotation.RIGHT -> revolver.rotate()
                    RevolverRotation.DONT -> { }
                }

                FourtyFiveLogger.debug(logTag, "revolver rotated $rotationDirection")
            }

            includeLater(
                { turnStatusEffectTimeline!! },
                { enemy.currentLives > 0 && turnStatusEffectTimeline != null }
            )

            includeLater(
                { finishTimeline },
                { enemy.currentLives > 0 }
            )

        }

        executeTimelineLater(Timeline.timeline {
            parallelActions(
                timeline.asAction(),
                gameRenderPipeline.getOnShotPostProcessingTimelineAction()
            )
        })
    }

    @AllThreadsAllowed
    fun checkCardModifierValidity() {
        FourtyFiveLogger.debug(logTag, "checking card modifiers")
        for (card in createdCards) if (card.inGame) card.checkModifierValidity()
    }

    @MainThreadOnly
    fun endTurn() {
        currentState.onEndTurn(this)
    }

    /**
     * damages the player (plays no animation, calls loose when lives go below 0)
     */
    @AllThreadsAllowed
    fun damagePlayer(damage: Int) {
        curPlayerLives -= damage
        FourtyFiveLogger.debug(logTag, "player got damaged; damage = $damage; curPlayerLives = $curPlayerLives")
        if (curPlayerLives <= 0) executeTimelineLater(Timeline.timeline {
            mainThreadAction { loose() }
        })
    }

    /**
     * adds reserves (plays no animations)
     */
    @AllThreadsAllowed
    fun gainReserves(amount: Int) {
        curReserves += amount
        FourtyFiveLogger.debug(logTag, "player gained reserves; amount = $amount; curReserves = $curReserves")
    }

    /**
     * changes the game to the destroy phase
     */
    @MainThreadOnly
    fun destroyCardPhase() = changeState(GameState.CardDestroy)

    /**
     * destroys a card in the revolver
     */
    @MainThreadOnly
    fun destroyCard(card: Card) {
        revolver.removeCard(card)
        card.onDestroy()
        FourtyFiveLogger.debug(logTag, "destroyed card: $card")
        checkEffectsSingleCard(Trigger.ON_DESTROY, card)
        currentState.onCardDestroyed(this)
    }

    /**
     * checks whether a destroyable card is in the game
     */
    fun hasDestroyableCard(): Boolean {
        for (card in createdCards) if (card.inGame && card.type == Card.Type.BULLET) {
            return true
        }
        return false
    }

    @MainThreadOnly
    private fun checkEffectsSingleCard(trigger: Trigger, card: Card) {
        FourtyFiveLogger.debug(logTag, "checking effects for card $card, trigger $trigger")
        card.checkEffects(trigger)?.let { executeTimelineLater(it) }
    }

    @MainThreadOnly
    fun checkEffectsActiveCards(trigger: Trigger) {
        FourtyFiveLogger.debug(logTag, "checking all active cards for trigger $trigger")
        val timeline = Timeline.timeline {
            for (card in createdCards) if (card.inGame) {
                val timeline = card.checkEffects(trigger)
                if (timeline != null) include(timeline)
            }
        }
        executeTimelineLater(timeline)
    }

    @MainThreadOnly
    fun checkStatusEffects() {
        FourtyFiveLogger.debug(logTag, "checking status effects")
        val timeline = Timeline.timeline {
            for (enemy in enemyArea.enemies) {
                val timeline = enemy.executeStatusEffects()
                if (timeline != null) include(timeline)
            }
        }
        executeTimelineLater(timeline)
    }

    /**
     * appends a timeline to the current timeline
     */
    @AllThreadsAllowed
    fun executeTimelineLater(timeline: Timeline) {
        for (action in timeline.actions) this.timeline.appendAction(action)
    }

    private fun freezeUI() {
        isUIFrozen = true
        FourtyFiveLogger.debug(logTag, "froze UI")
        val shootButton = shootButton
        val endTurnButton = endTurnButton
        if (shootButton is DisableActor) shootButton.isDisabled = true
        if (endTurnButton is DisableActor) endTurnButton.isDisabled = true
        for (card in cardHand.cards) card.isDraggable = false
    }

    private fun unfreezeUI() {
        isUIFrozen = false
        FourtyFiveLogger.debug(logTag, "unfroze UI")
        val shootButton = shootButton
        val endTurnButton = endTurnButton
        if (shootButton is DisableActor) shootButton.isDisabled = false
        if (endTurnButton is DisableActor) endTurnButton.isDisabled = false
        for (card in cardHand.cards) card.isDraggable = true
    }

    @AllThreadsAllowed
    fun showCardDrawActor() {
        FourtyFiveLogger.debug(logTag, "displaying card draw actor")
        val viewport = curScreen.stage.viewport
        val cardDrawActor = cardDrawActor
//        curScreen.addActorToRoot(cardDrawActor)
        cardDrawActor.isVisible = true
        cardDrawActor.setSize(viewport.worldWidth, viewport.worldHeight)
    }

    @AllThreadsAllowed
    fun showDestroyCardInstructionActor() {
        destroyCardInstructionActor.isVisible = true
    }

    @AllThreadsAllowed
    fun hideDestroyCardInstructionActor() {
        destroyCardInstructionActor.isVisible = false
    }

    @AllThreadsAllowed
    fun hideCardDrawActor() {
        FourtyFiveLogger.debug(logTag, "hiding card draw actor")
//        curScreen.removeActorFromRoot(cardDrawActor)
        cardDrawActor.isVisible = false
    }

    /**
     * draws a bullet from the stack
     */
    @AllThreadsAllowed
    fun drawBullet() {
        if (!currentState.allowsDrawingCards()) return
        val bullet = bulletStack.removeFirstOrNull() ?: defaultBullet.create()
        remainingBullets = bulletStack.size
        cardHand.addCard(bullet)
        FourtyFiveLogger.debug(logTag, "bullet was drawn; bullet = $bullet; cardsToDraw = $cardsToDraw")
        currentState.onCardDrawn(this)
    }


    /**
     * draws a cover from the stack
     */
    @AllThreadsAllowed
    fun drawCover() {
        if (!currentState.allowsDrawingCards()) return
        val cover = coverCardStack.removeFirstOrNull() ?: defaultCover.create()
        remainingCovers = coverCardStack.size
        cardHand.addCard(cover)
        FourtyFiveLogger.debug(logTag, "bullet was drawn; bullet = $cover; cardsToDraw = $cardsToDraw")
        currentState.onCardDrawn(this)
    }

    private fun cost(cost: Int): Boolean {
        if (cost > curReserves) return false
        curReserves -= cost
        SaveState.usedReserves += cost
        FourtyFiveLogger.debug(logTag, "$cost reserves were spent, curReserves = $curReserves")
        return true
    }

    override fun end() {
        FourtyFiveLogger.title("game ends")
        FourtyFive.currentGame = null
        SaveState.write()
    }

    /**
     * called when an enemy was defeated
     */
    @MainThreadOnly
    fun enemyDefeated(enemy: Enemy) {
        SaveState.enemiesDefeated++
        win()
    }

    @MainThreadOnly
    private fun win() {
        FourtyFiveLogger.debug(logTag, "player won")
        encounterMapEvent.completed()
        FourtyFive.changeToScreen(ScreenBuilder(Gdx.files.internal(winScreen)).build())
        SaveState.write()
    }

    @MainThreadOnly
    private fun loose() {
        FourtyFiveLogger.debug(logTag, "player lost")
        SaveState.reset()
        FourtyFive.changeToScreen(ScreenBuilder(Gdx.files.internal(looseScreen)).build())
    }

    enum class RevolverRotation {
        LEFT, RIGHT, DONT
    }

    companion object {

        const val logTag = "game"

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

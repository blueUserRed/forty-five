package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.GameScreenControllerDragAndDrop
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.game.enemy.EnemyArea
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.Timeline
import onj.*


/**
 * the Controller for the main game screen
 */
class GameScreenController(onj: OnjNamedObject) : ScreenController() {

    private val cardConfigFile = onj.get<String>("cardsFile")
    private val cardAtlasFile = onj.get<String>("cardAtlasFile")
    private val cardDragAndDropBehaviour = onj.get<OnjNamedObject>("cardDragBehaviour")
    private val cardHandOnj = onj.get<OnjObject>("cardHand")
    private val revolverOnj = onj.get<OnjObject>("revolver")
    private val enemyAreaOnj = onj.get<OnjObject>("enemyArea")
    private val coverAreaOnj = onj.get<OnjObject>("coverArea")
    private val enemiesOnj = onj.get<OnjArray>("enemies")
    private val cardDrawActorName = onj.get<String>("cardDrawActor")
    private val playerLivesLabelName = onj.get<String>("playerLivesLabelName")
    private val endTurnButtonName = onj.get<String>("playerLivesLabelName")
    private val shootButtonName = onj.get<String>("shootButtonName")

    private val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    private val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()
    private val basePlayerLives = onj.get<Long>("playerLives").toInt()

    var curScreen: ScreenDataProvider? = null
        private set

    var cardHand: CardHand? = null
    var revolver: Revolver? = null
    var enemyArea: EnemyArea? = null
    var coverArea: CoverArea? = null
    var cardDrawActor: Actor? = null
    var shootButton: Widget? = null
    var endTurnButton: Widget? = null
    var playerLivesLabel: CustomLabel? = null

    private var cards: List<Card> = listOf()
    private var bulletStack: MutableList<Card> = mutableListOf()
    private var coverCardStack: MutableList<Card> = mutableListOf()
    private var oneShotStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var enemies: List<Enemy> = listOf()

    private var remainingCardsToDraw: Int? = null

    var curPlayerLives: Int = basePlayerLives
        private set

    private var timeline: Timeline? = null
        set(value) {
            field = value
            value?.start()
        }

    /**
     * the current phase of the game
     */
    var currentPhase: Gamephase = Gamephase.FREE
        private set

    /**
     * counts up every round; starts at 0
     */
    var roundCounter: Int = 0
        private set

    override fun init(screenDataProvider: ScreenDataProvider) {
        curScreen = screenDataProvider
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cardAtlas = TextureAtlas(Gdx.files.internal(cardAtlasFile))

        for (region in cardAtlas.regions) {
            screenDataProvider.addTexture("${Card.cardTexturePrefix}${region.name}", region)
        }

        for (texture in cardAtlas.textures) screenDataProvider.addDisposable(texture)
        cards = Card.getFrom(onj.get<OnjArray>("cards"), screenDataProvider.textures)
        bulletStack = cards.filter { it.type == Card.Type.BULLET }.shuffled().toMutableList()
        coverCardStack = cards.filter { it.type == Card.Type.COVER }.shuffled().toMutableList()
        oneShotStack = cards.filter { it.type == Card.Type.ONE_SHOT }.shuffled().toMutableList()

        for (card in cards) {
            val behaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
                cardDragAndDropBehaviour.name,
                cardDragAndDrop,
                screenDataProvider,
                card.actor,
                cardDragAndDropBehaviour
            )
            if (behaviour is GameScreenControllerDragAndDrop) behaviour.gameScreenController = this
            cardDragAndDrop.addSource(behaviour)
        }

        enemies = Enemy.getFrom(enemiesOnj, screenDataProvider)

        cardDrawActor = screenDataProvider.namedActors[cardDrawActorName] ?: throw RuntimeException(
            "no actor with name $cardDrawActorName"
        )
        screenDataProvider.removeActorFromRoot(cardDrawActor!!)

        initCardHand()
        initPlayerLivesLabel()
        initRevolver()
        initEnemyArea()
        initCoverArea()

        for (behaviour in screenDataProvider.behaviours) if (behaviour is GameScreenBehaviour) {
            behaviour.gameScreenController = this
        }

        screenDataProvider.afterMs(5) { screenDataProvider.resortRootZIndices() } //TODO: this is really not good
//        changePhase(Gamephase.FREE)
        changePhase(Gamephase.INITIAL_DRAW)
    }

    private fun changePhase(next: Gamephase) {
        if (next == currentPhase) return
        currentPhase.transitionAway(this)
        currentPhase = next
        currentPhase.transitionTo(this)
    }

    override fun update() {
        timeline?.let {
            it.update()
            if (it.isFinished) timeline = null
        }
    }

    private fun initCardHand() {
        val curScreen = curScreen!!

        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActors[cardHandName]
            ?: throw RuntimeException("no named actor with name $cardHandName")
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        this.cardHand = cardHand
    }

    private fun initPlayerLivesLabel() {
        val curScreen = curScreen!!

        val playerLives = curScreen.namedActors[playerLivesLabelName]
            ?: throw RuntimeException("no named actor with name $playerLivesLabelName")
        if (playerLives !is CustomLabel) throw RuntimeException("actor named $playerLivesLabelName must be a Label")
        playerLivesLabel = playerLives
        updatePlayerLivesText()
    }

    private fun initCoverArea() {
        val curScreen = curScreen!!

        val coverAreaName = coverAreaOnj.get<String>("actorName")
        val coverArea = curScreen.namedActors[coverAreaName]
            ?: throw RuntimeException("no named actor with name $coverAreaName")
        if (coverArea !is CoverArea) throw RuntimeException("actor named $coverAreaName must be a CoverArea")
        this.coverArea = coverArea

        val dropOnj = coverAreaOnj.get<OnjNamedObject>("dropBehaviour")
        coverArea.slotDropConfig = cardDragAndDrop to dropOnj
    }

    private fun initRevolver() {
        val curScreen = curScreen!!

        val revolverName = revolverOnj.get<String>("actorName")
        val revolver = curScreen.namedActors[revolverName]
            ?: throw RuntimeException("no named actor with name $revolverName")
        if (revolver !is Revolver) throw RuntimeException("actor named $revolverName must be a Revolver")

        val dropOnj = revolverOnj.get<OnjNamedObject>("dropBehaviour")
        revolver.slotDropConfig = cardDragAndDrop to dropOnj

        this.revolver = revolver
    }

    private fun initEnemyArea() {
        val curScreen = curScreen!!

        val enemyAreaName = enemyAreaOnj.get<String>("actorName")
        val enemyArea = curScreen.namedActors[enemyAreaName] ?:
            throw RuntimeException("no named actor with name $enemyAreaName")
        if (enemyArea !is EnemyArea) throw RuntimeException("actor named $enemyAreaName must be a EnemyArea")

        enemyAreaOnj
            .get<OnjArray>("enemies")
            .value
            .forEach { nameOnj ->
                val name = nameOnj.value as String
                enemyArea.addEnemy(
                    enemies.firstOrNull { it.name == name} ?: throw RuntimeException("no enemy with name $name")
                )
            }

        this.enemyArea = enemyArea
    }

    /**
     * puts [card] in [slot] of the revolver (checks if the card is a bullet)
     */
    fun loadBulletInRevolver(card: Card, slot: Int) {
        if (card.type != Card.Type.BULLET) return
        cardHand!!.removeCard(card)
        revolver!!.setCard(slot, card)
    }

    /**
     * adds a new cover to a slot in the cover area (checks if the card is a cover)
     */
    fun addCover(card: Card, slot: Int) {
        if (card.type != Card.Type.COVER) return
        val addedCard = coverArea!!.addCover(card, slot, roundCounter)
        if (addedCard) cardHand!!.removeCard(card)
    }

    /**
     * shoots the revolver
     */
    fun shoot() {
        val cardToShoot = revolver!!.getCardInSlot(5)
        revolver!!.rotate()
        cardToShoot ?: return
        val enemy = enemyArea!!.enemies[0]
        enemy.damage(cardToShoot.baseDamage)
        revolver!!.removeCard(4)
    }

    fun endTurn() {
        onEndTurnButtonClicked()
    }

    fun damagePlayer(damage: Int) {
        curPlayerLives -= damage
        updatePlayerLivesText()
    }

    private fun updatePlayerLivesText() {
        playerLivesLabel!!.setText("lives: $curPlayerLives/$basePlayerLives")
    }

    private fun freezeUI() {
//        shootButton!!.isDisabled = true
    }

    private fun unfreezeUI() {
//        shootButton!!.isDisabled = false
    }

    private fun freezeCards() {
        for (card in cardHand!!.cards) card.isDraggable = false
    }

    private fun unfreezeCards() {
        for (card in cardHand!!.cards) card.isDraggable = true
    }

    private fun showCardDrawActor() {
        val viewport = curScreen!!.stage.viewport
        val cardDrawActor = cardDrawActor!!
        curScreen!!.addActorToRoot(cardDrawActor)
        cardDrawActor.isVisible = true
        cardDrawActor.setSize(viewport.worldWidth, viewport.worldHeight)
    }

    private fun hideCardDrawActor() {
        curScreen!!.removeActorFromRoot(cardDrawActor!!)
        cardDrawActor!!.isVisible = false
    }

    /**
     * draws a bullet from the stack
     */
    fun drawBullet() {
        var cardsToDraw = remainingCardsToDraw ?: return
        //TODO: default card when stack is empty
        cardHand!!.addCard(bulletStack.removeFirst())
        cardsToDraw--
        this.remainingCardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) onAllCardsDrawn()
    }


    /**
     * draws a cover from the stack
     */
    fun drawCover() {
        var cardsToDraw = remainingCardsToDraw ?: return
        //TODO: default card when stack is empty
        cardHand!!.addCard(coverCardStack.removeFirst())
        cardsToDraw--
        this.remainingCardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) onAllCardsDrawn()
    }

    override fun end() {
        curScreen = null
    }

    private fun onAllCardsDrawn() = changePhase(currentPhase.onAllCardsDrawn())

    private fun onEndTurnButtonClicked()  = changePhase(currentPhase.onEndTurnButtonClicked())


    /**
     * the phases of the game
     */
    enum class Gamephase {

        /**
         * draws cards at the beginning of the round
         */
        INITIAL_DRAW {

            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                roundCounter++
                freezeCards()
                freezeUI()
                showCardDrawActor()
                this.remainingCardsToDraw = if (roundCounter == 1) cardsToDrawInFirstRound else cardsToDraw
            }

            override fun transitionAway(gameScreenController: GameScreenController) = with(gameScreenController) {
                unfreezeCards()
                unfreezeUI()
                hideCardDrawActor()
                remainingCardsToDraw = null
            }

            override fun onAllCardsDrawn(): Gamephase = ENEMY_REVEAL
            override fun onEndTurnButtonClicked(): Gamephase = INITIAL_DRAW
        },

        /**
         * enemy reveals it's action
         */
        ENEMY_REVEAL {
            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                enemies[0].chooseNewAction()
                changePhase(FREE)
            }
            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = ENEMY_REVEAL
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_REVEAL
        },

        /**
         * main game phase
         */
        FREE {
            override fun transitionTo(gameScreenController: GameScreenController) {}
            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = FREE
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_ACTION
        },

        /**
         * enemy does its action
         */
        ENEMY_ACTION {

            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                timeline = Timeline.timeline {
                    include(enemies[0].doAction(gameScreenController))
                    delay(500)
                    action {
                        enemies[0].resetAction()
                        changePhase(INITIAL_DRAW)
                    }
                }
            }

            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = ENEMY_ACTION
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_ACTION
        }

        ;

        /**
         * transitions the game to this phase
         */
        abstract fun transitionTo(gameScreenController: GameScreenController)

        /**
         * transitions the game away from this phase
         */
        abstract fun transitionAway(gameScreenController: GameScreenController)

        /**
         * executed when all cards where drawn
         * @return the next phase
         */
        abstract fun onAllCardsDrawn(): Gamephase

        abstract fun onEndTurnButtonClicked(): Gamephase

    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

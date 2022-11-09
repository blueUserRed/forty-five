package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.GameScreenControllerDragAndDrop
import com.fourinachamber.fourtyfive.screen.*
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
    private val enemiesOnj = onj.get<OnjArray>("enemies")
    private val cardDrawActorName = onj.get<String>("cardDrawActor")
    private val shootButtonName = onj.get<String>("shootButtonName")
    private var curScreen: ScreenDataProvider? = null

    private var cardHand: CardHand? = null
    private var revolver: Revolver? = null
    private var enemyArea: EnemyArea? = null
    private var cardDrawActor: Actor? = null
    private var shootButton: CustomLabel? = null

    private var cards: List<Card> = listOf()
    private var bulletStack: MutableList<Card> = mutableListOf()
    private var coverCardStack: MutableList<Card> = mutableListOf()
    private var oneShotStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var enemies: List<Enemy> = listOf()

    private var cardsToDraw: Int? = null

//    var currentPhase: Gamephase = Gamephase.DRAW
//        private set

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

        enemies = Enemy.getFrom(enemiesOnj, screenDataProvider.textures, screenDataProvider.fonts)

        cardDrawActor = screenDataProvider.namedActors[cardDrawActorName] ?: throw RuntimeException(
            "no actor with name $cardDrawActorName"
        )

        val shootButton = screenDataProvider.namedActors[shootButtonName] ?: throw RuntimeException(
            "no actor with name $shootButtonName"
        )
        if (shootButton !is CustomLabel) throw RuntimeException("actor named $shootButtonName must be a Label")
        this.shootButton = shootButton

        initCardHand()
        initRevolver()
        initEnemyArea()

        for (behaviour in screenDataProvider.behaviours) if (behaviour is GameScreenBehaviour) {
            behaviour.gameScreenController = this
        }

        screenDataProvider.afterMs(1) { screenDataProvider.resortRootZIndices() } //TODO: this is not good
        startDrawPhase(6)
    }

    override fun update() {
    }

    private fun initCardHand() {
        val curScreen = curScreen!!

        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActors[cardHandName]
            ?: throw RuntimeException("no named actor with name $cardHandName")
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        this.cardHand = cardHand
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
                    enemies.firstOrNull { it.name ==  name} ?: throw RuntimeException("no enemy with name $name")
                )
            }

        this.enemyArea = enemyArea
    }

    fun moveCardFromHandToRevolver(card: Card, slot: Int) {
        cardHand!!.removeCard(card)
        revolver!!.setCard(slot, card)
    }

    fun shoot() {
//        if (currentPhase != Gamephase.FREE) return
        revolver!!.rotate()
    }

    private fun freezeUI() {
        shootButton!!.isDisabled = true
    }

    private fun unfreezeUI() {
        shootButton!!.isDisabled = false
    }

    private fun freezeCards() {
        for (card in cardHand!!.cards) card.isDraggable = false
    }

    private fun unfreezeCards() {
        for (card in cardHand!!.cards) card.isDraggable = true
    }

    private fun startDrawPhase(cardsToDraw: Int) {
//        currentPhase = Gamephase.DRAW
        freezeCards()
        freezeUI()
        val viewport = curScreen!!.stage.viewport
        val cardDrawActor = cardDrawActor!!
        cardDrawActor.isVisible = true
        cardDrawActor.setSize(viewport.worldWidth, viewport.worldHeight)
        this.cardsToDraw = cardsToDraw
    }

    private fun endDrawPhase() {
        unfreezeCards()
        unfreezeUI()
        cardDrawActor!!.isVisible = false
        cardsToDraw = null
    }

    fun drawBullet() {
        var cardsToDraw = cardsToDraw ?: return
        //TODO: default card when stack is empty
        cardHand!!.addCard(bulletStack.removeFirst())
        cardsToDraw--
        this.cardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) endDrawPhase()
    }

    fun drawCover() {
        var cardsToDraw = cardsToDraw ?: return
        //TODO: default card when stack is empty
        cardHand!!.addCard(coverCardStack.removeFirst())
        cardsToDraw--
        this.cardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) endDrawPhase()
    }

    override fun end() {
        curScreen = null
    }

    companion object {

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

//    enum class Gamephase {
//        DRAW, ENEMY_REVEAL, FREE, ENEMY_ACTION
//    }
}

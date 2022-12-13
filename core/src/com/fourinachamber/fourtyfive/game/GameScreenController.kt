package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.GameScreenControllerDragAndDrop
import com.fourinachamber.fourtyfive.game.enemy.Enemy
import com.fourinachamber.fourtyfive.game.enemy.EnemyArea
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.*
import onj.*
import kotlin.properties.Delegates


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
    private val destroyCardInstructionActorName = onj.get<String>("destroyCardInstructionActor")
    private val playerLivesLabelName = onj.get<String>("playerLivesLabelName")
    private val endTurnButtonName = onj.get<String>("endTurnButtonName")
    private val shootButtonName = onj.get<String>("shootButtonName")
    private val reservesLabelName = onj.get<String>("reservesLabelName")

    private val winScreen = onj.get<String>("winScreen")
    private val looseScreen = onj.get<String>("looseScreen")

    private val cardsToDrawInFirstRound = onj.get<Long>("cardsToDrawInFirstRound").toInt()
    private val cardsToDraw = onj.get<Long>("cardsToDraw").toInt()
    private val basePlayerLives = onj.get<Long>("playerLives").toInt()
    private val baseReserves = onj.get<Long>("reservesAtRoundBegin").toInt()
    private val maxCards = onj.get<Long>("maxCards").toInt()
    private val shotEmptyDamage = onj.get<Long>("shotEmptyDamage").toInt()

    var curScreen: ScreenDataProvider? = null
        private set

    var cardHand: CardHand? = null
    var revolver: Revolver? = null
    var enemyArea: EnemyArea? = null
    var coverArea: CoverArea? = null
    var cardDrawActor: Actor? = null
    var destroyCardInstructionActor: Actor? = null
    var shootButton: Actor? = null
    var endTurnButton: Actor? = null
    var playerLivesLabel: CustomLabel? = null
    var reservesLabel: CustomLabel? = null

    private var cards: List<Card> = listOf()
    private var bulletStack: MutableList<Card> = mutableListOf()
    private var coverCardStack: MutableList<Card> = mutableListOf()
    private var oneShotStack: MutableList<Card> = mutableListOf()
    private val cardDragAndDrop: DragAndDrop = DragAndDrop()

    private var enemies: List<Enemy> = listOf()

    private var remainingCardsToDraw: Int? = null

    private val playerLivesTemplate: TemplateString = TemplateString(
        playerLivesRawTemplateText,
        mapOf(
            "curLives" to { curPlayerLives },
            "baseLives" to { basePlayerLives }
        )
    )

    var curPlayerLives: Int = basePlayerLives
        private set(value) {
            field = value
            playerLivesLabel?.setText(playerLivesTemplate.string)
        }

    private val timeline: Timeline = Timeline(mutableListOf()).apply {
        start()
    }

    private var isUIFrozen: Boolean = false

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

    /**
     * counts up every revolver turn; starts at 0
     */
    var turnCounter: Int = 0
        private set

    private var cardsToDrawDuringSpecialDraw: Int = 1

    private val reservesTemplate: TemplateString = TemplateString(
        reservesRawTemplateText,
        mapOf(
            "curReserves" to { curReserves },
            "baseReserves" to { baseReserves }
        )
    )

    var curReserves: Int = 0
        private set(value) {
            field = value
            reservesLabel?.setText(reservesTemplate.string)
        }

    private var curGameAnims: MutableList<GameAnimation> = mutableListOf()

    private lateinit var defaultBulletCreator: () -> Card
    private lateinit var defaultCoverCreator: () -> Card

    private val playerStatusEffects: MutableList<StatusEffect> = mutableListOf()

    override fun init(screenDataProvider: ScreenDataProvider) {
        curScreen = screenDataProvider
        val onj = OnjParser.parseFile(cardConfigFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject

        val cardAtlas = TextureAtlas(Gdx.files.internal(cardAtlasFile))

        for (region in cardAtlas.regions) {
            screenDataProvider.addTexture("${Card.cardTexturePrefix}${region.name}", region)
        }

//        for (texture in cardAtlas.textures) screenDataProvider.addDisposable(texture)
        screenDataProvider.addDisposable(cardAtlas)
        cards = Card.getFrom(onj.get<OnjArray>("cards"), screenDataProvider.textures)
        bulletStack = cards.filter { it.type == Card.Type.BULLET }.shuffled().toMutableList()
        coverCardStack = cards.filter { it.type == Card.Type.COVER }.shuffled().toMutableList()
        oneShotStack = cards.filter { it.type == Card.Type.ONE_SHOT }.shuffled().toMutableList()
        initDefaultCards(onj)

        for (card in cards) doDragAndDropFor(card)

        enemies = Enemy.getFrom(enemiesOnj, this)

        cardDrawActor = screenDataProvider.namedActors[cardDrawActorName] ?: throw RuntimeException(
            "no actor with name $cardDrawActorName"
        )
        screenDataProvider.removeActorFromRoot(cardDrawActor!!)

        destroyCardInstructionActor = screenDataProvider.namedActors[destroyCardInstructionActorName]
            ?: throw RuntimeException("no actor with name $destroyCardInstructionActorName")
        destroyCardInstructionActor!!.isVisible = false

        initButtons()
        initCardHand()
        initLabels()
        initRevolver()
        initEnemyArea()
        initCoverArea()

        for (behaviour in screenDataProvider.behaviours) if (behaviour is GameScreenBehaviour) {
            behaviour.gameScreenController = this
        }

        screenDataProvider.afterMs(10) { screenDataProvider.resortRootZIndices() } //TODO: this is really not good
        changePhase(Gamephase.INITIAL_DRAW)
    }

    private fun doDragAndDropFor(card: Card) {
        val behaviour = DragAndDropBehaviourFactory.dragBehaviourOrError(
            cardDragAndDropBehaviour.name,
            cardDragAndDrop,
            curScreen!!,
            card.actor,
            cardDragAndDropBehaviour
        )
        if (behaviour is GameScreenControllerDragAndDrop) behaviour.gameScreenController = this
        cardDragAndDrop.addSource(behaviour)
    }

    private fun changePhase(next: Gamephase) {
        if (next == currentPhase) return
        currentPhase.transitionAway(this)
        currentPhase = next
        currentPhase.transitionTo(this)
    }

    override fun update() {
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

    fun playGameAnimation(anim: GameAnimation) {
        anim.start()
        curGameAnims.add(anim)
    }

    fun specialDraw(amount: Int) {
        if (currentPhase != Gamephase.FREE) return
        cardsToDrawDuringSpecialDraw = amount
        changePhase(Gamephase.SPECIAL_DRAW)
    }

    private fun initDefaultCards(onj: OnjObject) {

        val bulletOnj = onj.get<OnjObject>("defaultBullet")
        val bulletName = bulletOnj.get<String>("name")
        val bulletTitle = bulletOnj.get<String>("title")
        val bulletDescription = bulletOnj.get<String>("description")
        val bulletDamage = bulletOnj.get<Long>("baseDamage").toInt()
        val bulletCost = bulletOnj.get<Long>("cost").toInt()

        defaultBulletCreator = {
            val card = Card(
                bulletName,
                bulletTitle,
                curScreen!!.textures["${Card.cardTexturePrefix}$bulletName"] ?:
                    throw RuntimeException("no texture found for default card: $bulletName"),
                bulletDescription,
                Card.Type.BULLET,
                bulletDamage,
                0,
                bulletCost,
                bulletOnj.get<OnjArray>("effects")
                    .value
                    .map { (it as OnjExtensions.OnjEffect).value }
            )
            for (effect in card.effects) effect.card = card
            doDragAndDropFor(card)
            Card.applyTraitEffects(card, bulletOnj)
            card
        }

        val coverOnj = onj.get<OnjObject>("defaultCover")
        val coverName = coverOnj.get<String>("name")
        val coverTitle = coverOnj.get<String>("title")
        val coverDescription = coverOnj.get<String>("description")
        val coverValue = coverOnj.get<Long>("coverValue").toInt()
        val coverCost = coverOnj.get<Long>("cost").toInt()

        defaultCoverCreator = {
            val card = Card(
                coverName,
                coverTitle,
                curScreen!!.textures["${Card.cardTexturePrefix}$coverName"] ?:
                throw RuntimeException("no texture found for default card: $coverName"),
                coverDescription,
                Card.Type.COVER,
                0,
                coverValue,
                coverCost,
                coverOnj.get<OnjArray>("effects")
                    .value
                    .map { (it as OnjExtensions.OnjEffect).value }
            )
            for (effect in card.effects) effect.card = card
            doDragAndDropFor(card)
            card
        }
    }

    private fun initButtons() {
        shootButton = curScreen!!.namedActors[shootButtonName]
            ?: throw RuntimeException("no actor named $shootButtonName")
        endTurnButton = curScreen!!.namedActors[endTurnButtonName]
            ?: throw RuntimeException("no actor named $endTurnButton")
    }

    private fun initCardHand() {
        val curScreen = curScreen!!

        val cardHandName = cardHandOnj.get<String>("actorName")
        val cardHand = curScreen.namedActors[cardHandName]
            ?: throw RuntimeException("no named actor with name $cardHandName")
        if (cardHand !is CardHand) throw RuntimeException("actor named $cardHandName must be a CardHand")
        this.cardHand = cardHand
    }

    private fun initLabels() {
        val curScreen = curScreen!!

        val playerLives = curScreen.namedActors[playerLivesLabelName]
            ?: throw RuntimeException("no named actor with name $playerLivesLabelName")
        if (playerLives !is CustomLabel) throw RuntimeException("actor named $playerLivesLabelName must be a Label")
        playerLivesLabel = playerLives
        curPlayerLives = curPlayerLives // inits label

        val reserves = curScreen.namedActors[reservesLabelName]
            ?: throw RuntimeException("no named actor with name $reservesLabelName")
        if (reserves !is CustomLabel) throw RuntimeException("actor named $reservesLabelName must be a Label")
        reservesLabel = reserves
        curReserves = curReserves // inits label
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
        if (card.type != Card.Type.BULLET || !card.allowsEnteringGame(this)) return
        if (!cost(card.cost)) return
        cardHand!!.removeCard(card)
        revolver!!.setCard(slot, card)
        card.onEnter(this)
        checkEffectsSingleCard(Trigger.ON_ENTER, card)
    }

    /**
     * adds a new cover to a slot in the cover area (checks if the card is a cover)
     */
    fun addCover(card: Card, slot: Int) {
        if (card.type != Card.Type.COVER || !card.allowsEnteringGame(this)) return
        if (!cost(card.cost)) return
        val addedCard = coverArea!!.addCover(card, slot, roundCounter)
        if (addedCard) cardHand!!.removeCard(card)
        card.onEnter(this)
        checkEffectsSingleCard(Trigger.ON_ENTER, card)
    }

    /**
     * shoots the revolver
     */
    fun shoot() {
        val revolver = revolver!!
        turnCounter++

        val cardToShoot = revolver.getCardInSlot(5)
        val rotateLeft = cardToShoot?.shouldRotateLeft ?: false
        if (rotateLeft) revolver.rotateLeft() else revolver.rotate()

        var enemyDamageTimeline: Timeline? = null
        var statusEffectTimeline: Timeline? = null
        var effectTimeline: Timeline? = null

        if (cardToShoot != null) {

            val enemy = enemyArea!!.enemies[0]

            enemyDamageTimeline = Timeline.timeline {
                action {
                    if (cardToShoot.shouldRemoveAfterShot) {
                        revolver.removeCard(if (rotateLeft) 1 else 4)
                    }
                }
                include(enemy.damage(cardToShoot.curDamage, this@GameScreenController))
                action { cardToShoot.afterShot(this@GameScreenController) }
            }

            statusEffectTimeline = enemy.executeStatusEffectsAfterDamage(this, cardToShoot.curDamage)
            effectTimeline = cardToShoot.checkEffects(Trigger.ON_SHOT, this)
        }

        val timeline = Timeline.timeline {

            includeLater(
                { enemies[0].damagePlayer(shotEmptyDamage, this@GameScreenController) },
                { cardToShoot == null }
            )

            enemyDamageTimeline?.let { include(it) }
            statusEffectTimeline?.let { include(it) }
            effectTimeline?.let { include(it) }

            action {
                checkCardModifierValidity()

                revolver
                    .slots
                    .mapNotNull { it.card }
                    .forEach { it.onRevolverTurn(it === cardToShoot) }

                enemies.forEach(Enemy::onRevolverTurn)
            }
        }
        executeTimelineLater(timeline)
    }

    private fun checkCardModifierValidity() {
        for (card in cards) if (card.inGame) card.checkModifierValidity()
    }

    fun endTurn() {
        onEndTurnButtonClicked()
    }

    fun damagePlayer(damage: Int) {
        curPlayerLives -= damage
        if (curPlayerLives <= 0) executeTimelineLater(Timeline.timeline {
            action { loose() }
        })
    }

    fun gainReserves(amount: Int) {
        curReserves += amount
    }

    fun destroyCardPhase() = changePhase(Gamephase.CARD_DESTROY)

    fun destroyCard(card: Card) {
        card.onDestroy()
        revolver!!.removeCard(card)
//        coverArea!!.removeCard(card)
        onCardDestroyed()
    }

    fun hasDestroyableCard(): Boolean {
        for (card in cards) if (card.inGame && card.type == Card.Type.BULLET) {
            return true
        }
        return false
    }

    private fun checkEffectsSingleCard(trigger: Trigger, card: Card) {
        card.checkEffects(trigger, this)?.let { executeTimelineLater(it) }
    }

    private fun checkEffectsActiveCards(trigger: Trigger) {
        val timeline = Timeline.timeline {
            for (card in cards) if (card.inGame) {
                val timeline = card.checkEffects(trigger, this@GameScreenController)
                if (timeline != null) include(timeline)
            }
        }
        executeTimelineLater(timeline)
    }

    private fun checkStatusEffects() {
        val timeline = Timeline.timeline {
            for (enemy in enemies) {
                val timeline = enemy.executeStatusEffects(this@GameScreenController)
                if (timeline != null) include(timeline)
            }
        }
        executeTimelineLater(timeline)
    }

    fun executeTimelineImmediate(timeline: Timeline) {
        for (action in timeline.actions.reversed()) this.timeline.pushAction(action)
    }

    fun executeTimelineLater(timeline: Timeline) {
        for (action in timeline.actions) this.timeline.appendAction(action)
    }

    private fun freezeUI() {
        isUIFrozen = true
        val shootButton = shootButton
        val endTurnButton = endTurnButton
        if (shootButton is DisableActor) shootButton.isDisabled = true
        if (endTurnButton is DisableActor) endTurnButton.isDisabled = true
        for (card in cardHand!!.cards) card.isDraggable = false
    }

    private fun unfreezeUI() {
        isUIFrozen = false
        val shootButton = shootButton
        val endTurnButton = endTurnButton
        if (shootButton is DisableActor) shootButton.isDisabled = false
        if (endTurnButton is DisableActor) endTurnButton.isDisabled = false
        for (card in cardHand!!.cards) card.isDraggable = true
    }

    private fun showCardDrawActor() {
        val viewport = curScreen!!.stage.viewport
        val cardDrawActor = cardDrawActor!!
        curScreen!!.addActorToRoot(cardDrawActor)
        cardDrawActor.isVisible = true
        cardDrawActor.setSize(viewport.worldWidth, viewport.worldHeight)
    }

    private fun showDestroyCardInstructionActor() {
        destroyCardInstructionActor!!.isVisible = true
    }

    private fun hideDestroyCardInstructionActor() {
        destroyCardInstructionActor!!.isVisible = false
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
        cardHand!!.addCard(bulletStack.removeFirstOrNull() ?: defaultBulletCreator())
        cardsToDraw--
        this.remainingCardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) onAllCardsDrawn()
    }


    /**
     * draws a cover from the stack
     */
    fun drawCover() {
        var cardsToDraw = remainingCardsToDraw ?: return
        cardHand!!.addCard(coverCardStack.removeFirstOrNull() ?: defaultCoverCreator())
        cardsToDraw--
        this.remainingCardsToDraw = cardsToDraw
        if (cardsToDraw <= 0) onAllCardsDrawn()
    }

    private fun cost(cost: Int): Boolean {
        if (cost > curReserves) return false
        curReserves -= cost
        return true
    }

    override fun end() {
        curScreen = null
    }

    fun enemyDefeated(enemy: Enemy): Unit = win()

    private fun win() {
        FourtyFive.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(winScreen)).build()
    }

    private fun loose() {
        FourtyFive.curScreen = ScreenBuilderFromOnj(Gdx.files.internal(looseScreen)).build()
    }

    private fun onAllCardsDrawn() = changePhase(currentPhase.onAllCardsDrawn())

    private fun onEndTurnButtonClicked() = changePhase(currentPhase.onEndTurnButtonClicked())

    private fun onCardDestroyed() = changePhase(currentPhase.onCardDestroyed())


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
                remainingCardsToDraw =
                    (if (roundCounter == 1) cardsToDrawInFirstRound else cardsToDraw)
                        .coerceAtMost(maxCards - cardHand!!.cards.size)
                if (remainingCardsToDraw == 0) return //TODO: display this in some way
                showCardDrawActor()
            }

            override fun transitionAway(gameScreenController: GameScreenController) = with(gameScreenController) {
                hideCardDrawActor()
                remainingCardsToDraw = null
                checkStatusEffects()
                checkCardModifierValidity()
            }

            override fun onAllCardsDrawn(): Gamephase = ENEMY_REVEAL
            override fun onEndTurnButtonClicked(): Gamephase = INITIAL_DRAW
            override fun onCardDestroyed(): Gamephase = INITIAL_DRAW
        },

        /**
         * draws cards during the round, e.g. because of effects
         */
        SPECIAL_DRAW {

            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                showCardDrawActor()
                this.remainingCardsToDraw = cardsToDrawDuringSpecialDraw
            }

            override fun transitionAway(gameScreenController: GameScreenController) = with(gameScreenController) {
                hideCardDrawActor()
                remainingCardsToDraw = null
            }

            override fun onAllCardsDrawn(): Gamephase = FREE
            override fun onEndTurnButtonClicked(): Gamephase = SPECIAL_DRAW
            override fun onCardDestroyed(): Gamephase = SPECIAL_DRAW
        },

        CARD_DESTROY {

            private var postProcessor: PostProcessor? = null

            private var previousPostProcessor: PostProcessor? = null

            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {

                if (postProcessor == null) {
                    postProcessor = curScreen!!.postProcessors[destroyCardsPostProcessorName]
                        ?: throw RuntimeException("unknown postProcessor: $destroyCardsPostProcessorName")
                }

                showDestroyCardInstructionActor()

                previousPostProcessor = curScreen!!.postProcessor
                curScreen!!.postProcessor = postProcessor

                for (card in cards) if (card.inGame && card.type == Card.Type.BULLET) {
                    card.enterDestroyMode(this)
                }
            }

            override fun transitionAway(gameScreenController: GameScreenController) = with(gameScreenController) {
                hideDestroyCardInstructionActor()
                curScreen!!.postProcessor = previousPostProcessor
                for (card in cards) if (card.inGame && card.type == Card.Type.BULLET) {
                    card.leaveDestroyMode()
                }
            }

            override fun onAllCardsDrawn(): Gamephase = CARD_DESTROY
            override fun onEndTurnButtonClicked(): Gamephase = CARD_DESTROY
            override fun onCardDestroyed(): Gamephase = FREE

        },

        /**
         * enemy reveals it's action
         */
        ENEMY_REVEAL {
            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                enemies[0].chooseNewAction()
                curReserves = baseReserves
                checkEffectsActiveCards(Trigger.ON_ROUND_START)
                changePhase(FREE)
            }
            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = ENEMY_REVEAL
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_REVEAL
            override fun onCardDestroyed(): Gamephase = ENEMY_REVEAL
        },

        /**
         * main game phase
         */
        FREE {
            override fun transitionTo(gameScreenController: GameScreenController) {}
            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = FREE
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_ACTION
            override fun onCardDestroyed(): Gamephase = FREE
        },

        /**
         * enemy does its action
         */
        ENEMY_ACTION {

            override fun transitionTo(gameScreenController: GameScreenController) = with(gameScreenController) {
                val timeline = Timeline.timeline {

                    val enemyBannerAnim = BannerAnimation(
                        curScreen!!.textures[enemyTurnBannerName]!!,
                        curScreen!!,
                        bannerAnimDuration,
                        bannerScaleAnimDuration,
                        bannerBeginScale,
                        bannerEndScale
                    )
                    val playerBannerAnim = BannerAnimation(
                        curScreen!!.textures[playerTurnBannerName]!!,
                        curScreen!!,
                        bannerAnimDuration,
                        bannerScaleAnimDuration,
                        bannerBeginScale,
                        bannerEndScale
                    )

                    action { playGameAnimation(enemyBannerAnim) }
                    delayUntil { enemyBannerAnim.isFinished() }
                    delay(bufferTime)
                    enemies[0].doAction(gameScreenController)?.let { include(it) }
                    delay(bufferTime)
                    action {
                        enemies[0].resetAction()
                        playGameAnimation(playerBannerAnim)
                    }
                    delayUntil { playerBannerAnim.isFinished() }
                    delay(bufferTime)
                    action { changePhase(INITIAL_DRAW) }
                }

                executeTimelineLater(timeline)
            }

            override fun transitionAway(gameScreenController: GameScreenController) {}
            override fun onAllCardsDrawn(): Gamephase = ENEMY_ACTION
            override fun onEndTurnButtonClicked(): Gamephase = ENEMY_ACTION
            override fun onCardDestroyed(): Gamephase = ENEMY_ACTION
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
        abstract fun onCardDestroyed(): Gamephase

    }

    companion object {

        private var bufferTime by Delegates.notNull<Int>()

        private var bannerAnimDuration by Delegates.notNull<Int>()
        private var bannerScaleAnimDuration by Delegates.notNull<Int>()
        private var bannerBeginScale by Delegates.notNull<Float>()
        private var bannerEndScale by Delegates.notNull<Float>()

        private var xShake by Delegates.notNull<Float>()
        private var yShake by Delegates.notNull<Float>()
        private var xShakeSpeedMultiplier by Delegates.notNull<Float>()
        private var yShakeSpeedMultiplier by Delegates.notNull<Float>()
        private var shakeDuration by Delegates.notNull<Float>()

        private lateinit var dmgFontName: String
        private lateinit var dmgFontColor: Color
        private var dmgFontScale by Delegates.notNull<Float>()
        private var dmgDuration by Delegates.notNull<Int>()
        private var dmgRaiseHeight by Delegates.notNull<Float>()
        private var dmgStartFadeoutAt by Delegates.notNull<Int>()

        private var xQuickCharge by Delegates.notNull<Float>()
        private var yQuickCharge by Delegates.notNull<Float>()
        private var quickChargeDuration by Delegates.notNull<Float>()
        private lateinit var quickChargeInterpolation: Interpolation

        private lateinit var playerTurnBannerName: String
        private lateinit var enemyTurnBannerName: String

        private lateinit var destroyCardsPostProcessorName: String

        private lateinit var playerLivesRawTemplateText: String
        private lateinit var reservesRawTemplateText: String

        fun init(config: OnjObject) {

            bufferTime = (config.get<Double>("bufferTime") * 1000).toInt()

            val bannerOnj = config.get<OnjObject>("bannerAnimation")

            bannerAnimDuration = (bannerOnj.get<Double>("duration") * 1000).toInt()
            bannerScaleAnimDuration = (bannerOnj.get<Double>("scaleAnimDuration") * 1000).toInt()
            bannerBeginScale = bannerOnj.get<Double>("beginScale").toFloat()
            bannerEndScale = bannerOnj.get<Double>("endScale").toFloat()

            playerTurnBannerName = bannerOnj.get<String>("playerTurnBanner")
            enemyTurnBannerName = bannerOnj.get<String>("enemyTurnBanner")

            val tmplOnj = config.get<OnjObject>("stringTemplates")

            playerLivesRawTemplateText = tmplOnj.get<String>("playerLives")
            reservesRawTemplateText = tmplOnj.get<String>("reserves")

            val plOnj = config.get<OnjObject>("playerLivesAnimation")

            dmgFontName = plOnj.get<String>("font")
            dmgFontScale = plOnj.get<Double>("fontScale").toFloat()
            dmgDuration = (plOnj.get<Double>("duration") * 1000).toInt()
            dmgRaiseHeight = plOnj.get<Double>("raiseHeight").toFloat()
            dmgStartFadeoutAt = (plOnj.get<Double>("startFadeoutAt") * 1000).toInt()
            dmgFontColor = Color.valueOf(plOnj.get<String>("negativeFontColor"))

            val shakeOnj = config.get<OnjObject>("shakeAnimation")

            xShake = shakeOnj.get<Double>("xShake").toFloat()
            yShake = shakeOnj.get<Double>("yShake").toFloat()
            xShakeSpeedMultiplier = shakeOnj.get<Double>("xSpeed").toFloat()
            yShakeSpeedMultiplier = shakeOnj.get<Double>("ySpeed").toFloat()
            shakeDuration = shakeOnj.get<Double>("duration").toFloat()

            val chargeOnj = config.get<OnjObject>("enemyQuickChargeAnimation")

            xQuickCharge = chargeOnj.get<Double>("xCharge").toFloat()
            yQuickCharge = chargeOnj.get<Double>("yCharge").toFloat()
            quickChargeDuration = chargeOnj.get<Double>("duration").toFloat() / 2f // divide by two because anim is played twice
            quickChargeInterpolation = Utils.interpolationOrError(chargeOnj.get<String>("interpolation"))

            destroyCardsPostProcessorName = config.get<OnjObject>("destroyCardPostProcessor").get<String>("name")

        }

        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }

    }

}

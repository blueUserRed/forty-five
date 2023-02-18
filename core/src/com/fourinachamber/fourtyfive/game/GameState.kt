package com.fourinachamber.fourtyfive.game

import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.PostProcessor
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.multipleTemplateParam

sealed class GameState {

    class InitialDraw(cardsToDraw: Int) : GameState() {

        private var remainingCardsToDraw: Int by multipleTemplateParam(
        "game.remainingCardsToDraw", cardsToDraw,
        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
        )

        override fun transitionTo(controller: GameController) = with(controller) {
            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
            FourtyFiveLogger.debug(logTag, "drawing cards in initial draw: $remainingCardsToDraw")
            if (remainingCardsToDraw == 0) { //TODO: display this in some way
                changeState(Free)
                return
            }
            showCardDrawActor()
        }

        override fun transitionAway(controller: GameController) = with(controller) {
            hideCardDrawActor()
            checkStatusEffects()
            checkCardModifierValidity()

            enemies[0].chooseNewAction()
            curReserves = baseReserves
            checkEffectsActiveCards(Trigger.ON_ROUND_START)
        }

        override fun allowsDrawingCards(): Boolean = true

        override fun shouldIncrementRoundCounter(): Boolean = true

        override fun onCardDrawn(controller: GameController) {
            remainingCardsToDraw--
            if (remainingCardsToDraw <= 0) {
                controller.changeState(Free)
            }
        }

        companion object {
            const val logTag = "game-InitialDraw"
        }

    }

    class SpecialDraw(val cardsToDraw: Int) : GameState() {

        private var remainingCardsToDraw: Int by multipleTemplateParam(
        "game.remainingCardsToDraw", cardsToDraw,
        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
        )

        override fun transitionTo(controller: GameController) = with(controller) {
            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
            FourtyFiveLogger.debug(logTag, "drawing cards in special draw: $remainingCardsToDraw")
            if (remainingCardsToDraw == 0) { //TODO: display this in some way
                changeState(Free)
                return
            }
            showCardDrawActor()
        }

        override fun transitionAway(controller: GameController) = with(controller) {
            hideCardDrawActor()
        }

        override fun allowsDrawingCards(): Boolean = true

        override fun onCardDrawn(controller: GameController) {
            remainingCardsToDraw--
            if (remainingCardsToDraw <= 0) {
                controller.changeState(Free)
            }
        }

        companion object {
            const val logTag = "game-SpecialDraw"
        }

    }

    object CardDestroy : GameState() {

        private var destroyCardPostProcessor: PostProcessor? = null
        private var previousPostProcessor: PostProcessor? = null

        private fun getDestroyCardPostProcessor(controller: GameController): PostProcessor {
            val destroyCardPostProcessor = destroyCardPostProcessor
            if (destroyCardPostProcessor != null) return destroyCardPostProcessor
            val fromManager = ResourceManager.get<PostProcessor>(controller.curScreen, "destroyCardPostProcessor")
            this.destroyCardPostProcessor = fromManager
            return fromManager
        }

        override fun transitionTo(controller: GameController) = with(controller) {
            showDestroyCardInstructionActor()
            previousPostProcessor = curScreen.postProcessor
            curScreen.postProcessor = getDestroyCardPostProcessor(this)
            createdCards
                .filter { it.inGame && it.type == Card.Type.BULLET }
                .forEach(Card::enterDestroyMode)
        }

        override fun transitionAway(controller: GameController) = with(controller) {
            hideDestroyCardInstructionActor()
            curScreen.postProcessor = previousPostProcessor
            createdCards
                .filter { it.inGame && it.type == Card.Type.BULLET }
                .forEach(Card::leaveDestroyMode)
        }

        override fun onCardDestroyed(controller: GameController) {
            controller.changeState(Free)
        }
    }

    object Free : GameState() {

        override fun allowsShooting(): Boolean = true

        override fun onEndTurn(controller: GameController) {
            controller.changeState(EnemyAction)
        }
    }

    object EnemyAction : GameState() {

        override fun transitionTo(controller: GameController) = with(controller) {
            val timeline = Timeline.timeline {
                val screen = curScreen
                val enemyBannerAnim = GraphicsConfig.bannerAnimation(false, screen)
                val playerBannerAnim = GraphicsConfig.bannerAnimation(true, screen)
                includeAction(enemyBannerAnim)
                delay(GraphicsConfig.bufferTime)
                enemies[0].doAction()?.let { include(it) }
                delay(GraphicsConfig.bufferTime)
                action { enemies[0].resetAction() }
                includeAction(playerBannerAnim)
                delay(GraphicsConfig.bufferTime)
                action { changeState(InitialDraw(cardsToDraw)) }
            }
            executeTimelineLater(timeline)
        }
    }



    open fun transitionTo(controller: GameController) { }
    open fun transitionAway(controller: GameController) { }

    open fun allowsShooting(): Boolean = false
    open fun allowsDrawingCards(): Boolean = false

    open fun shouldIncrementRoundCounter(): Boolean = false

    open fun onEndTurn(controller: GameController) { }
    open fun onCardDestroyed(controller: GameController) { }
    open fun onCardDrawn(controller: GameController) { }

    override fun equals(other: Any?): Boolean {
        return other != null && this::class == other::class
    }

    override fun hashCode(): Int = this::class.hashCode()

    override fun toString(): String = this::class.simpleName ?: "<anonymous>"
}

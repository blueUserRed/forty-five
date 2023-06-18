package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.utils.*

sealed class GameState {

    class InitialDraw(cardsToDraw: Int) : GameState() {

        private var remainingCardsToDraw: Int by multipleTemplateParam(
        "game.remainingCardsToDraw", cardsToDraw,
        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
        )

        override fun transitionTo(controller: GameController) = with(controller) {
            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
            FortyFiveLogger.debug(logTag, "drawing cards in initial draw: $remainingCardsToDraw")
            if (remainingCardsToDraw == 0) executeTimeline(Timeline.timeline {
                include(confirmationPopup("Hand reached maximum of $maxCards"))
                action { changeState(Free) }
            }) else {
                showCardDrawActor()
            }
        }

        override fun transitionAway(controller: GameController) = with(controller) {
            hideCardDrawActor()
            checkStatusEffects()
            checkCardModifierValidity()

            curReserves = baseReserves
            checkEffectsActiveCards(Trigger.ON_ROUND_START)
        }

        override fun allowsDrawingCards(): Boolean = true

        override fun shouldIncrementTurnCounter(): Boolean = true

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
            FortyFiveLogger.debug(logTag, "drawing cards in special draw: $remainingCardsToDraw")
            if (remainingCardsToDraw == 0) executeTimeline(Timeline.timeline {
                include(confirmationPopup("Hand reached maximum of $maxCards"))
                action { changeState(Free) }
            }) else {
                showCardDrawActor()
            }
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

        // TODO: rework using popup

        override fun transitionTo(controller: GameController) = with(controller) {
//            showDestroyCardInstructionActor()
//            gameRenderPipeline.enterDestroyMode()
//            createdCards
//                .filter { it.inGame && it.type == Card.Type.BULLET }
//                .forEach(Card::enterDestroyMode)
        }

        override fun transitionAway(controller: GameController) = with(controller) {
//            hideDestroyCardInstructionActor()
//            gameRenderPipeline.leaveDestroyMode()
//            createdCards
//                .filter { it.inGame && it.type == Card.Type.BULLET }
//                .forEach(Card::leaveDestroyMode)
        }

        override fun onCardDestroyed(controller: GameController) {
            controller.changeState(Free)
        }
    }

    object Free : GameState() {

        override fun allowsShooting(): Boolean = true

        override fun transitionTo(controller: GameController) {
            controller.gameDirector.checkActions()
        }

        override fun onEndTurn(controller: GameController) {
            controller.changeState(InitialDraw(controller.cardsToDraw))
        }
    }

    object EnemyAction : GameState() {

        override fun transitionTo(controller: GameController) = with(controller) {
            // TODO: repurpose this for 'plottwist'-enemy-action
//            val timeline = Timeline.timeline {
//                val screen = curScreen
//                val enemyBannerAnim = GraphicsConfig.bannerAnimation(false, screen)
//                val playerBannerAnim = GraphicsConfig.bannerAnimation(true, screen)
//                includeAction(enemyBannerAnim)
//                delay(GraphicsConfig.bufferTime)
//                enemyArea.enemies.forEach { enemy ->
//                    enemy.doAction()?.let { include(it) }
//                }
//                delay(GraphicsConfig.bufferTime)
//                action { enemyArea.enemies.forEach(Enemy::resetAction) }
//                includeAction(playerBannerAnim)
//                delay(GraphicsConfig.bufferTime)
//                action { changeState(InitialDraw(cardsToDraw)) }
//            }
//            executeTimelineLater(timeline)
        }
    }


    @MainThreadOnly
    open fun transitionTo(controller: GameController) { }

    @MainThreadOnly
    open fun transitionAway(controller: GameController) { }

    @AllThreadsAllowed
    open fun allowsShooting(): Boolean = false

    @AllThreadsAllowed
    open fun allowsDrawingCards(): Boolean = false

    @AllThreadsAllowed
    open fun shouldIncrementTurnCounter(): Boolean = false

    @MainThreadOnly
    open fun onEndTurn(controller: GameController) { }

    @MainThreadOnly
    open fun onCardDestroyed(controller: GameController) { }

    @MainThreadOnly
    open fun onCardDrawn(controller: GameController) { }

    override fun equals(other: Any?): Boolean {
        return other != null && this::class == other::class
    }

    override fun hashCode(): Int = this::class.hashCode()

    override fun toString(): String = this::class.simpleName ?: "<anonymous>"
}

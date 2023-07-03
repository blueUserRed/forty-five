package com.fourinachamber.fortyfive.game

import com.fourinachamber.fortyfive.utils.*

sealed class GameState {

//    class InitialDraw(cardsToDraw: Int) : GameState() {
//
//        private var remainingCardsToDraw: Int by multipleTemplateParam(
//        "game.remainingCardsToDraw", cardsToDraw,
//        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
//        )
//
//        override fun transitionTo(controller: GameController) = with(controller) {
//            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
//            FortyFiveLogger.debug(logTag, "drawing cards in initial draw: $remainingCardsToDraw")
//            if (remainingCardsToDraw == 0) executeTimeline(Timeline.timeline {
//                include(maxCardsPopup())
//                action { changeState(Free) }
//            }) else {
//                showCardDrawActor()
//            }
//        }
//
//        override fun transitionAway(controller: GameController) = with(controller) {
//            hideCardDrawActor()
//            checkCardModifierValidity()
//            val actionTimeline = controller.gameDirector.checkActions()
//
//            curReserves = baseReserves
//            controller.executeTimeline(Timeline.timeline {
//                include(actionTimeline)
//                includeLater({ checkStatusEffects() }, { true })
//                includeLater({ checkEffectsActiveCards(Trigger.ON_ROUND_START) }, { true })
//            })
//        }
//
//        override fun allowsDrawingCards(): Boolean = true
//
//        override fun onCardDrawn(controller: GameController) {
//            remainingCardsToDraw--
//            if (remainingCardsToDraw <= 0) {
//                controller.changeState(Free)
//            }
//        }
//
//        companion object {
//            const val logTag = "game-InitialDraw"
//        }
//
//    }
//
//    class SpecialDraw(val cardsToDraw: Int) : GameState() {
//
//        private var remainingCardsToDraw: Int by multipleTemplateParam(
//        "game.remainingCardsToDraw", cardsToDraw,
//        "game.remainingCardsToDrawPluralS" to { if (it == 1) "" else "s" }
//        )
//
//        override fun transitionTo(controller: GameController) = with(controller) {
//            remainingCardsToDraw = remainingCardsToDraw.coerceAtMost(maxCards - cardHand.cards.size)
//            FortyFiveLogger.debug(logTag, "drawing cards in special draw: $remainingCardsToDraw")
//            if (remainingCardsToDraw == 0) executeTimeline(Timeline.timeline {
//                include(maxCardsPopup())
//                action { changeState(Free) }
//            }) else {
//                showCardDrawActor()
//            }
//        }
//
//        override fun transitionAway(controller: GameController) = with(controller) {
//            hideCardDrawActor()
//        }
//
//        override fun allowsDrawingCards(): Boolean = true
//
//        override fun onCardDrawn(controller: GameController) {
//            remainingCardsToDraw--
//            if (remainingCardsToDraw <= 0) {
//                controller.changeState(Free)
//            }
//        }
//
//        companion object {
//            const val logTag = "game-SpecialDraw"
//        }
//
//    }
//
//    object Free : GameState() {
//
//        override fun allowsShooting(): Boolean = true
//
//        override fun transitionTo(controller: GameController) {
//        }
//
//        override fun onEndTurn(controller: GameController) {
//            controller.nextTurn()
//            if (!controller.playerLost) controller.changeState(InitialDraw(controller.cardsToDraw))
//        }
//    }
//
//    @MainThreadOnly
//    open fun transitionTo(controller: GameController) { }
//
//    @MainThreadOnly
//    open fun transitionAway(controller: GameController) { }
//
//    @AllThreadsAllowed
//    open fun allowsShooting(): Boolean = false
//
//    @AllThreadsAllowed
//    open fun allowsDrawingCards(): Boolean = false
//
//    @MainThreadOnly
//    open fun onEndTurn(controller: GameController) { }
//
//    @MainThreadOnly
//    open fun onCardDestroyed(controller: GameController) { }
//
//    @MainThreadOnly
//    open fun onCardDrawn(controller: GameController) { }
//
//    override fun equals(other: Any?): Boolean {
//        return other != null && this::class == other::class
//    }
//
//    override fun hashCode(): Int = this::class.hashCode()
//
//    override fun toString(): String = this::class.simpleName ?: "<anonymous>"
}

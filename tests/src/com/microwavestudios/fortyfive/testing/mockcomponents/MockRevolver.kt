package com.microwavestudios.fortyfive.testing.mockcomponents

import com.badlogic.gdx.math.Vector2
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.game.widgets.IRevolver
import com.microwavestudios.fortyfive.game.widgets.IRevolverSlot
import com.microwavestudios.fortyfive.game.widgets.Revolver
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot
import com.microwavestudios.fortyfive.testing.notAvailableInMock
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.Timeline

class MockRevolver : IRevolver {

    override val slots: Array<out MockRevolverSlot> = Array(5) {
        MockRevolverSlot(it + 1, this)
    }

    override fun setCard(slot: Int, card: Card?) {
        getSlot(slot).card = card
    }

    override fun preAddCard(slot: Int, card: Card) {
    }

    override fun removeCard(slot: Int) {
        getSlot(slot).card = null
    }

    override fun removeCard(card: Card) {
        slots.find { it.card === card }?.card = null
    }

    override fun getCardInSlot(slot: Int): Card? = getSlot(slot).card

    override fun getCardTriggerPosition(): Vector2 = Vector2(0f, 0f)
    override fun getMirroredCardTriggerPosition(): Vector2 = Vector2(0f, 0f)
    override fun getCardOnShotTriggerPosition(): Vector2 = Vector2(0f, 0f)

    override fun rotate(rotation: RevolverRotation): Timeline = Timeline.timeline {
        action {
            when (rotation) {
                is RevolverRotation.Right -> repeat(rotation.amount) {
                    rotateRight()
                }
                is RevolverRotation.Left -> repeat(rotation.amount) {
                    rotateLeft()
                }
                else -> {}
            }
        }
    }

    private fun rotateRight() {
        val firstCard = slots[0].card
        slots[0].card = slots[1].card
        slots[1].card = slots[2].card
        slots[2].card = slots[3].card
        slots[3].card = slots[4].card
        slots[4].card = firstCard
    }

    private fun rotateLeft() {
        val firstCard = slots[4].card
        slots[4].card = slots[3].card
        slots[3].card = slots[2].card
        slots[2].card = slots[1].card
        slots[1].card = slots[0].card
        slots[0].card = firstCard
    }

    override fun forceGetActor(): Revolver = notAvailableInMock()

    private fun getSlot(num: Int): MockRevolverSlot {
        require(num in 1..5) { "slot number must be in 1..5" }
        return slots[num - 1]
    }

}

class MockRevolverSlot(
    override val num: Int,
    override val revolver: IRevolver
) : IRevolverSlot {

    override var card: Card? = null

    override fun cardPosition(): Vector2 = Vector2(0f, 0f)

    override fun forceGetActor(): RevolverSlot = notAvailableInMock()

    override fun enterSelectionMode(promise: Promise<IRevolverSlot>) {
    }

    override fun exitSelectionMode() {
    }

}

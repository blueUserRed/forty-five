package com.microwavestudios.fortyfive.testing.mockcomponents

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.CardPresentation
import com.microwavestudios.fortyfive.game.card.PresentationProvider
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.testing.notAvailableInMock
import com.microwavestudios.fortyfive.utils.Timeline

class MockCardPresentation : CardPresentation {

    override var inTriggerPosition: Boolean = false
        private set

    override var isMarked: Boolean = false

    override var isDraggable: Boolean = true

    override var touchable: Touchable = Touchable.disabled

    override fun setAlphaZero() {
    }

    override fun setAlphaOne() {
    }

    override fun position(): Vector2 = Vector2(0f, 0f)

    override fun destroyAnimation(): Timeline = Timeline.emptyTimeline

    override fun spawnAnimation(reverse: Boolean): Timeline = Timeline.emptyTimeline

    override fun descendAnimation(): Timeline = Timeline.emptyTimeline

    override fun resetDescendAnimation() {
    }

    override fun forceGetActor(): CardActor = notAvailableInMock()

    override fun redrawPixmap(damageValue: Int, costValue: Int) {
    }

    override fun animateToTriggerPosition(
        controller: GameController,
        isOnShot: Boolean,
        afterlifeOpen: Boolean
    ): Timeline = Timeline.timeline {
        action {
            inTriggerPosition = false
        }
    }

    override fun animateBack(
        controller: GameController,
        prevCoordinates: Vector2
    ): Timeline = Timeline.timeline {
        action {
            inTriggerPosition = false
        }
    }

    override fun skipAnimateBack() {
        inTriggerPosition = false
    }

    override fun dispose() {
    }

    companion object {
        val mockProvider: PresentationProvider = { _, _ -> MockCardPresentation() }
    }
}

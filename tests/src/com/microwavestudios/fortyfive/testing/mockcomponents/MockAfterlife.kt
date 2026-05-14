package com.microwavestudios.fortyfive.testing.mockcomponents

import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.widgets.IAfterlife
import com.microwavestudios.fortyfive.utils.Timeline

class MockAfterlife : IAfterlife {

    private val _cards: MutableList<Card> = mutableListOf()
    override val cards: List<Card>
        get() = _cards

    override var isOpen: Boolean = false
        private set
    override val isClosed: Boolean
        get() = !isOpen

    override var afterlifeIsVisible: Boolean = false
        private set

    override fun pushCard(card: Card) {
        _cards.add(card)
    }

    override fun scrollToBeginTimeline(): Timeline = Timeline.emptyTimeline

    override fun popCardTimeline(): Timeline = Timeline.timeline {
        action {
            if (_cards.isEmpty()) return@action
            _cards.removeAt(0)
        }
    }

    override fun toggleTimeline(): Timeline = Timeline.timeline {
        action {
            afterlifeIsVisible = true
            isOpen = !isOpen
        }
    }

    override fun openTimeline(): Timeline = Timeline.timeline {
        action {
            afterlifeIsVisible = true
            isOpen =  true
        }
    }

    override fun closeTimeline(): Timeline = Timeline.timeline {
        action {
            isOpen = false
        }
    }
}

package com.microwavestudios.fortyfive.testing.mockcomponents

import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.utils.Disposable
import com.microwavestudios.fortyfive.rendering.DebugMenu
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.testing.notAvailableInMock
import com.microwavestudios.fortyfive.utils.Either
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Lifetime
import com.microwavestudios.fortyfive.utils.eitherRight

class MockScreen : IScreen {

    override val screenEvents: EventPipeline = EventPipeline()
    override var defaultCursor: Either<Cursor, Cursor.SystemCursor> = Cursor.SystemCursor.Arrow.eitherRight()

    private val _screenState: MutableSet<String> = mutableSetOf()
    override val screenState: Set<String>
        get() = _screenState

    private val _lifetime: EndableLifetime = EndableLifetime()
    override val lifetime: Lifetime
        get() = _lifetime

    override var debugMenu: DebugMenu? = null
    override val events: EventPipeline = screenEvents

    private val _screenControllers: MutableList<ScreenController> = mutableListOf()
    override val screenControllers: List<ScreenController>
        get() = _screenControllers

    override fun addScreenController(controller: ScreenController) {
        controller.onActive()
        controller.onShow()
        _screenControllers.add(controller)
    }

    override fun afterMs(ms: Int, callback: () -> Unit) = notAvailableInMock()

    override fun addDisposable(disposable: Disposable) {
        lifetime.tieDisposable(disposable)
    }

    override fun enterState(state: String) {
        _screenState.add(state)
    }

    override fun leaveState(state: String) {
        _screenState.remove(state)
    }

    override fun addOnScreenStateChangedListener(listener: (entered: Boolean, state: String) -> Unit) =
        notAvailableInMock()
}
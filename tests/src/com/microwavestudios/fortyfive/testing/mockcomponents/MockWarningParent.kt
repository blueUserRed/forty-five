package com.microwavestudios.fortyfive.testing.mockcomponents

import com.microwavestudios.fortyfive.screen.commonComponents.IWarningParent

class MockWarningParent : IWarningParent {

    override fun showTemporaryWarning(
        text: String,
        level: IWarningParent.Level,
        time: Int
    ): IWarningParent.IWarning = MockWarning(text, level)

    override fun showTemporaryWarning(
        warning: IWarningParent.IWarning,
        time: Int
    ) {
    }

    override fun show(warning: IWarningParent.IWarning) {
    }

    override fun hide(warning: IWarningParent.IWarning) {
    }

    override fun warning(
        text: String,
        level: IWarningParent.Level
    ): IWarningParent.IWarning = MockWarning(text, level)

    class MockWarning(
        val text: String,
        val level: IWarningParent.Level
    ) : IWarningParent.IWarning {

        override var isActive: Boolean = false
            private set

        override fun show() {
            isActive = true
        }

        override fun hide() {
            isActive = false
        }

    }
}
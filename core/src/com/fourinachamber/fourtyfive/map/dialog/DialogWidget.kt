package com.fourinachamber.fourtyfive.map.dialog

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.AdvancedTextWidget
import com.fourinachamber.fourtyfive.screen.general.AdvancedText
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.TextAdvancedTextPart

class DialogWidget(
    advancedText: AdvancedText,
    fontScale: Float,
    private val progressTime: Int,
    screen: OnjScreen
) : AdvancedTextWidget(advancedText, fontScale, screen) {

    private var isAnimFinished: Boolean = false

    private var lastProgressTime: Long = Long.MAX_VALUE

    override var advancedText: AdvancedText
        get() = super.advancedText
        set(value) {
            super.advancedText = value
            value.resetProgress()
            isAnimFinished = false
        }

    init {
        advancedText.resetProgress()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (isAnimFinished) return
        val curTime = TimeUtils.millis()
        if (curTime < lastProgressTime + progressTime) return
        isAnimFinished = advancedText.progress()
        lastProgressTime = curTime
    }
}

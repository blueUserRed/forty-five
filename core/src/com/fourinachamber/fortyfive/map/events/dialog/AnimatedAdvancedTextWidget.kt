package com.fourinachamber.fortyfive.map.events.dialog

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.general.AdvancedText
import com.fourinachamber.fortyfive.screen.general.AdvancedTextWidget
import com.fourinachamber.fortyfive.screen.general.OnjScreen

class AnimatedAdvancedTextWidget(
    defaults: Triple<String, Color, Float>,
    screen: OnjScreen,
    isDistanceField: Boolean,
) : AdvancedTextWidget(defaults, screen, isDistanceField) {

    var progressTimeMs: Int = 10

    private var lastProgressTime: Long = Long.MIN_VALUE

    var isFinished: Boolean = true
        private set

    private var onPartFinished: MutableList<() -> Unit> = mutableListOf()

    override var advancedText: AdvancedText
        get() = super.advancedText
        set(value) {
            super.advancedText = value
            value.resetProgress()
            isFinished = false
        }

    fun onPartFinished(callback: () -> Unit) {
        onPartFinished.add(callback)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        advancedText.update()

        if (isFinished) return
        val curTime = TimeUtils.millis()
        if (curTime < lastProgressTime + progressTimeMs) return
        isFinished = advancedText.progress()
        if (isFinished) {
            onPartFinished.forEach { it.invoke() }
        }
        lastProgressTime = curTime
    }
}
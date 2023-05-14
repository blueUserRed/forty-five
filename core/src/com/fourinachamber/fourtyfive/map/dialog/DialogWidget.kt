package com.fourinachamber.fourtyfive.map.dialog

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.screen.general.*

class DialogWidget(
    private val dialog: Dialog,
    fontScale: Float,
    private val progressTime: Int,
    screen: OnjScreen
) : AdvancedTextWidget(dialog.currentPart!!.text, fontScale, screen) {

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
        if (isAnimFinished) finished()
        lastProgressTime = curTime
    }

    private fun finished() {
        val next = dialog.next() ?: return
        advancedText = next.text
        advancedText.resetProgress()
        isAnimFinished = false
    }

}

data class Dialog(
    val firstPart: DialogPart
) {

    var currentPart: DialogPart? = firstPart
        private set

    fun next(): DialogPart? {
        val selector = currentPart?.nextDialogPartSelector ?: run {
            currentPart = null
            return null
        }
        val next = when (selector) {

            is NextDialogPartSelector.Fixed -> selector.next
            NextDialogPartSelector.End -> null

            else -> TODO()

        }
        currentPart = next
        return next
    }

}

data class DialogPart(
    val text: AdvancedText,
    val nextDialogPartSelector: NextDialogPartSelector
)

sealed class NextDialogPartSelector {

    class Fixed(val next: DialogPart) : NextDialogPartSelector()

    class Choice(
        val firstText: String,
        val secondText: String,
        val firstPart: DialogPart,
        val secondPart: DialogPart
    ) : NextDialogPartSelector()

    object End : NextDialogPartSelector()

}

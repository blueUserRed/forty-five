package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OffSettable

class CustomMoveByAction(
    var target: OffSettable? = null,
    interpolation: Interpolation = Interpolation.linear,
    var relX: Float = 0F,
    var relY: Float = 0F,
    duration: Float = 1000F,
) : RelativeTemporalAction() {
    init {
        super.setInterpolation(interpolation)
        super.setDuration(duration / 1000)
    }

    private var lastDist = 0F

    override fun updateRelative(percentDelta: Float) {
    }

    override fun update(interpol: Float) {
        super.update(interpol)
        val target = this.target
        target as CustomFlexBox
        val curDist = interpol - lastDist
        target.offsetX += curDist * relX
        target.offsetY += curDist * relY
        lastDist = interpol
    }
}
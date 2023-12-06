package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.RelativeTemporalAction
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox

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
        val target = this.target ?: return
        target as OffSettable
        val curDist = interpol - lastDist
        target.offsetX += curDist * relX
        target.offsetY += curDist * relY
        lastDist = interpol
    }
}

//
//class CustomMoveToAction(
//    var target: CustomFlexBox? = null,
//    interpolation: Interpolation = Interpolation.linear,
//    private val startX: Float? = null,
//    private val startY: Float? = null,
//    private val endX: Float? = null,
//    private val endY: Float? = null,
//    duration: Float = 1000F,
//) : RelativeTemporalAction() {
//    init {
//        super.setInterpolation(interpolation)
//        super.setDuration(duration / 1000)
//    }
//
//    private var lastDist = 0F
//
//    override fun updateRelative(percentDelta: Float) {
//    }
//
//    private var distX: Float? = null
//    private var distY: Float? = null
//    override fun begin() {
//        super.begin()
//        distX = if (endX != null) {
//            if (startX != null) target.x = startX!!
//            startX!! - target.x
//        } else null
//
//        distY = if (endY != null) {
//            if (startY != null) target.y = startY!!
//            startY!! - target.y
//        } else null
//    }
//
//    override fun update(interpol: Float) {
//        super.update(interpol)
//        val target = this.target ?: return
//        target as CustomFlexBox
//        val curDist = interpol - lastDist
//        target.x += curDist * (distX ?: 0F)
//        target.y += curDist * (distY ?: 0F)
//        lastDist = interpol
//    }
//}
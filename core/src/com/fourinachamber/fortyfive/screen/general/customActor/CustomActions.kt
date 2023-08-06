package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fortyfive.screen.general.OffSettable
import com.fourinachamber.fortyfive.utils.Timeline

class CustomMoveByAction(
    var target: OffSettable? = null,
    var interpolation: Interpolation = Interpolation.linear,
    var relX: Float = 0F,
    var relY: Float = 0F,
    var duration: Int = 1000,
) {
    fun getTimeline(): Timeline {
        val target = this.target ?: return Timeline(mutableListOf())
        var lastOff = 0F
        val nbrOfRotations = (duration / (1000F / Gdx.graphics.framesPerSecond)).toInt() + 1
        movedDistance = 0F
        return Timeline.timeline {
            repeat(nbrOfRotations) {
                lastOff = addAction(it.toFloat() / nbrOfRotations, lastOff, target)
            }
            addAction(1F, lastOff, target)
        }
    }

    private fun Timeline.TimelineBuilderDSL.addAction(
        progress: Float,
        lastOff: Float,
        target: OffSettable
    ): Float {
        val interpolDist = interpolation.apply(progress)
        val curDist = interpolDist - lastOff
        action {
            target.offsetX += curDist * relX
            target.offsetY += curDist * relY
            movedDistance += curDist
        }
        delay(((1000F / Gdx.graphics.framesPerSecond)).toInt())
        return interpolDist
    }

    companion object {
        var movedDistance = 0F
    }
}
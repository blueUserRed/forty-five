package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.*
import kotlin.math.sin

/**
 * an object which is rendered and to which a mask can be applied
 */
interface Maskable {

    /**
     * the mask to apply
     */
    var mask: Texture?

    /**
     * by default only parts where the mask is opaque will be rendered, but if invert is set to true, only parts where
     * the mask is not opaque are rendered
     */
    var invert: Boolean

    /**
     * scales the mask horizontally
     */
    var maskScaleX: Float

    /**
     * scales the mask vertically
     */
    var maskScaleY: Float

    /**
     * offsets the mask horizontally
     */
    var maskOffsetX: Float

    /**
     * offsets the mask vertically
     */
    var maskOffsetY: Float
}

/**
 * an actor that can be disabled
 */
interface DisableActor {

    /**
     * true if the actor is disabled
     */
    var isDisabled: Boolean
}

/**
 * The default implementation of z-indices in libgdx is really bad, so here is my own.
 * Actors that implement this interface can have z-indices applied.
 * Only works when the actor is in a [ZIndexGroup]
 */
interface ZIndexActor {

    /**
     * the actor with the higher z-index is rendered on top
     */
    var fixedZIndex: Int
}

/**
 * A group that supports [ZIndexActor]. [resortZIndices] must be called after an actor is added for the z-indices to
 * work correctly
 */
interface ZIndexGroup {

    /**
     * resorts the children according to their z-indices; has to be called after adding an actor
     */
    fun resortZIndices()
}

interface BoundedActor {

    /**
     * returns the area of the actor on the screen in worldSpace coordinates
     */
    fun getBounds(): Rectangle

    /**
     * returns the area of the actor on the screen in screenSpace coordinates
     */
    fun getScreenSpaceBounds(screen: OnjScreen): Rectangle {
        val worldSpaceBounds = getBounds()
        val worldSpaceCoords = Vector2(worldSpaceBounds.x, worldSpaceBounds.y)
        val screenSpaceCoords = screen.viewport.project(worldSpaceCoords)
        val (screenSpaceWidth, screenSpaceHeight) =
            Utils.worldSpaceToScreenSpaceDimensions(worldSpaceBounds.width, worldSpaceBounds.height, screen.viewport)
        return Rectangle(screenSpaceCoords.x, screenSpaceCoords.y, screenSpaceWidth, screenSpaceHeight)
    }
}

interface AnimatedActor {

    val screen: OnjScreen

    val animationsNeedingUpdate: MutableList<NeedsUpdate>

    fun updateAnimations() {
        animationsNeedingUpdate.forEach { it.update() }
    }

    fun animateUpAndDownSinus(
        method: AnimationMethod = AnimationMethod.X_AND_Y,
        amplitude: Float = 20f,
        frequency: Float = 0.5f,
        phase: Float = (0f..(2f * Math.PI.toFloat())).random(),
        offset: Float = 0f
    ) {
        this as Actor
        val initialY = y
        val time = TimeUtils.millis()
        val updater = NeedsUpdate {
            val value = sin(TimeUtils.timeSinceMillis(time).toFloat() / 1000f * frequency + phase) * amplitude + offset
            when (method) {
                AnimationMethod.DRAW_OFFSET -> {
                    this as? OffSettable ?: throw RuntimeException("actor must be OffSettable to use method $method")
                    drawOffsetY = value
                }
                AnimationMethod.LOGICAL_OFFSET -> {
                    this as? OffSettable ?: throw RuntimeException("actor must be OffSettable to use method $method")
                    logicalOffsetY = value
                    (this as? Layout)?.invalidateHierarchy()
                }
                AnimationMethod.X_AND_Y -> y = initialY + value
            }
        }
        animationsNeedingUpdate.add(updater)
    }

    fun animateLeftAndRightSinus(
        method: AnimationMethod = AnimationMethod.X_AND_Y,
        amplitude: Float = 20f,
        frequency: Float = 0.5f,
        phase: Float = (0f..(2f * Math.PI.toFloat())).random(),
        offset: Float = 0f
    ) {
        this as Actor
        val initialX = x
        val time = TimeUtils.millis()
        val updater = NeedsUpdate {
            val value = sin(TimeUtils.timeSinceMillis(time).toFloat() / 1000f * frequency + phase) * amplitude + offset
            when (method) {
                AnimationMethod.DRAW_OFFSET -> {
                    this as? OffSettable ?: throw RuntimeException("actor must be OffSettable to use method $method")
                    drawOffsetX = value
                }
                AnimationMethod.LOGICAL_OFFSET -> {
                    this as? OffSettable ?: throw RuntimeException("actor must be OffSettable to use method $method")
                    logicalOffsetX = value
                    (this as? Layout)?.invalidateHierarchy()
                }
                AnimationMethod.X_AND_Y -> x = initialX + value
            }
        }
        animationsNeedingUpdate.add(updater)
    }

    fun animateRotationSinus(
        amplitude: Float = Math.PI.toFloat() * 2,
        frequency: Float = 1f,
        phase: Float = (0f..(2f * Math.PI.toFloat())).random(),
        offset: Float = 0f
    ) {
        this as Actor
        if (this is Group) {
            isTransform = true
        }
        val time = TimeUtils.millis()
        val updater = NeedsUpdate {
            val value = sin(TimeUtils.timeSinceMillis(time).toFloat() / 1000f * frequency + phase) * amplitude + offset
            rotation = value
        }
        animationsNeedingUpdate.add(updater)
    }

    fun interface NeedsUpdate {
        fun update()
    }

    enum class AnimationMethod {
        X_AND_Y,
        LOGICAL_OFFSET,
        DRAW_OFFSET
    }
}

interface OffSettable {
    fun resetAllOffsets() {
        drawOffsetX = 0F
        drawOffsetY = 0F
        logicalOffsetX = 0F
        logicalOffsetY = 0F
    }

    var drawOffsetX: Float
    var drawOffsetY: Float
    var logicalOffsetX: Float
    var logicalOffsetY: Float
}

interface OnLayoutActor {

    fun onLayout(callback: () -> Unit)
}

interface HasPaddingActor {
    var paddingTop: Float
    var paddingBottom: Float
    var paddingLeft: Float
    var paddingRight: Float

    fun setPadding(value: Number) {
        val v = value.toFloat()
        paddingTop = v
        paddingBottom = v
        paddingRight = v
        paddingLeft = v
    }
}

interface KotlinStyledActor {
    var marginTop: Float //These are all to set the data
    var marginBottom: Float
    var marginLeft: Float
    var marginRight: Float

    var positionType: PositionType

    fun setMargin(value: Number) {
        val v = value.toFloat()
        marginTop = v
        marginBottom = v
        marginRight = v
        marginLeft = v
    }
}

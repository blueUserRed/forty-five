package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.screen.RenderableScreen
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
    fun getScreenSpaceBounds(screen: RenderableScreen): Rectangle {
        val worldSpaceBounds = getBounds()
        val worldSpaceCoords = Vector2(worldSpaceBounds.x, worldSpaceBounds.y)
        val screenSpaceCoords = screen.viewport.project(worldSpaceCoords)
        val (screenSpaceWidth, screenSpaceHeight) =
            Utils.worldSpaceToScreenSpaceDimensions(worldSpaceBounds.width, worldSpaceBounds.height, screen.viewport)
        return Rectangle(screenSpaceCoords.x, screenSpaceCoords.y, screenSpaceWidth, screenSpaceHeight)
    }
}

interface AnimatedActor {

    val screen: RenderableScreen

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
    ): AnimationController {
        this as Actor
        val time = TimeUtils.millis()
        val updater = NeedsUpdate {
            val value = sin(TimeUtils.timeSinceMillis(time).toFloat() / 1000f * frequency + phase) * amplitude + offset
            method.set(false, this, value)
        }
        animationsNeedingUpdate.add(updater)
        return AnimMethodBasedAnimationController(this, false, method, updater)
    }

    fun animateLeftAndRightSinus(
        method: AnimationMethod = AnimationMethod.X_AND_Y,
        amplitude: Float = 20f,
        frequency: Float = 0.5f,
        phase: Float = (0f..(2f * Math.PI.toFloat())).random(),
        offset: Float = 0f,
        interpolation: Interpolation = Interpolation.linear
    ): AnimationController {
        this as Actor
        val time = TimeUtils.millis()
        val updater = NeedsUpdate {
            val sin = sin(TimeUtils.timeSinceMillis(time).toFloat() / 1000f * frequency + phase)
            val value = (interpolation.apply((sin + 1) / 2) * 2 - 1) * amplitude + offset
            method.set(true, this, value)
        }
        animationsNeedingUpdate.add(updater)
        return AnimMethodBasedAnimationController(this, true, method, updater)
    }

    fun animateRotationSinus(
        amplitude: Float = Math.PI.toFloat() * 2,
        frequency: Float = 1f,
        phase: Float = (0f..(2f * Math.PI.toFloat())).random(),
        offset: Float = 0f
    ): AnimationController {
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

        return object : AnimationController {

            var isRunning = true

            override fun start() {
                if (isRunning) return
                isRunning = true
                animationsNeedingUpdate.add(updater)
            }

            override fun stop() {
                if (!isRunning) return
                isRunning = false
                animationsNeedingUpdate.remove(updater)
            }

            override fun reset() {
                rotation = 0f
            }
        }
    }

    fun interface NeedsUpdate {
        fun update()
    }

    interface AnimationController {
        fun start()
        fun stop()
        fun reset()
    }

    private class AnimMethodBasedAnimationController(
        val actor: AnimatedActor,
        val isX: Boolean,
        val method: AnimationMethod,
        val updater: NeedsUpdate
    ) : AnimationController {

        private var isRunning = true

        override fun start() {
            if (isRunning) return
            isRunning = true
            actor.animationsNeedingUpdate.add(updater)
        }

        override fun stop() {
            if (!isRunning) return
            isRunning = false
            actor.animationsNeedingUpdate.remove(updater)
        }

        override fun reset() {
            actor as Actor
            method.set(isX, actor, 0f)
        }
    }

    enum class AnimationMethod {
        X_AND_Y {
            override fun set(isX: Boolean, actor: Actor, value: Float) {
                if (isX) actor.x = value
                else actor.y = value
            }
        },
        LOGICAL_OFFSET {
            override fun set(isX: Boolean, actor: Actor, value: Float) {
                if (actor !is OffSettable) return
                if (isX) actor.logicalOffsetX = value
                else actor.logicalOffsetY = value
                (actor as? Layout)?.invalidateHierarchy()
            }
        },
        DRAW_OFFSET {
            override fun set(isX: Boolean, actor: Actor, value: Float) {
                if (actor !is OffSettable) return
                if (isX) actor.drawOffsetX = value
                else actor.drawOffsetY = value
            }
        };

        abstract fun set(isX: Boolean, actor: Actor, value: Float)
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

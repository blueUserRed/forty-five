package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2
import ktx.actors.onClick
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.value.OnjValue

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

/**
 * A Class for all possible widgets which want to be shown by [com.fourinachamber.fortyfive.map.statusbar.StatusbarWidget],
 * so that it can call the display and hide timelines when pressing the corresponding button
 */
interface InOutAnimationActor {

    fun display(): Timeline

    fun hide(): Timeline
}

/**
 * an actor that can be in an animation
 */
interface AnimationActor {

    /**
     * true if the actor is in an animation. If so, it should be treated differently, e.g. by not setting its position
     */
    var inAnimation: Boolean
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
        val screenSpaceWidth = (screen.viewport.screenWidth / screen.viewport.worldWidth) * worldSpaceBounds.width
        val screenSpaceHeight = (screen.viewport.screenHeight / screen.viewport.worldHeight) * worldSpaceBounds.height
        return Rectangle(screenSpaceCoords.x, screenSpaceCoords.y, screenSpaceWidth, screenSpaceHeight)
    }
}

/**
 * an actor that can be selected using the keyboard
 */
interface KeySelectableActor : BoundedActor {

    /**
     * true when the actor is currently selected
     */
    var isSelected: Boolean

    /**
     * true when the actor wants to be part of the hierarchy used to determine the next actor.
     * When this is false, the actor cannot be selected
     */
    val partOfHierarchy: Boolean

}

/**
 * Actor that can keep track of whether it is hovered over or not
 */
interface HoverStateActor {

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean

    /**
     * if it was clicked and therefore is still hovered over (so it doesn't stop showing hover if it )
     */
    var isClicked: Boolean

    /**
     * binds listeners to [actor] that automatically assign [isHoveredOver]
     */
    fun bindHoverStateListeners(actor: Actor) {
        actor.onEnter { isHoveredOver = true }
        actor.onClick { isClicked = true }
        actor.onExit {
            if (!isClicked) isHoveredOver = false
            isClicked = false
        }
    }

}

/**
 * actor that has a background that can be changed
 */
interface BackgroundActor {

    /**
     * handle of the current background
     */
    var backgroundHandle: ResourceHandle?

}

/**
 * Actor that can be detached from the screen and then reattached
 */
interface Detachable {

    val attached: Boolean

    fun detach()
    fun reattach()
}

interface OffSettable {
    var offsetX: Float
    var offsetY: Float
}

interface DisplayDetailsOnHoverActor {

    var actorTemplate: String
    var detailActor: Actor?

    fun <T> registerOnHoverDetailActor(
        actor: T,
        screen: OnjScreen
    ) where T : DisplayDetailsOnHoverActor, T : Actor = screen.addOnHoverDetailActor(actor)

    fun setBoundsOfHoverDetailActor(actor: Actor) {
        val detailActor = detailActor
        if (detailActor !is Layout) return
        val prefHeight = detailActor.prefHeight
        val prefWidth = detailActor.prefWidth
        val (x, y) = actor.localToStageCoordinates(Vector2(0f, 0f))
        detailActor.setBounds(
            x + actor.width / 2 - detailActor.width / 2,
            y + actor.height,
            if (prefWidth == 0f) detailActor.width else prefWidth,
            prefHeight
        )
        detailActor.invalidateHierarchy()
    }

    fun getHoverDetailData(): Map<String, OnjValue>

    fun onDetailDisplayStarted() {}
    fun onDetailDisplayEnded() {}

}

interface AnimationSpawner {

    val actor: Actor
}

interface ActorWithAnimationSpawners {

    val actor: Actor

    val animationSpawners: List<AnimationSpawner>

    fun addAnimationSpawner(spawner: AnimationSpawner)

    fun layoutSpawners(xPos: Float, yPos: Float, width: Float, height: Float) {
        val (x, y) = actor.localToStageCoordinates(Vector2(xPos, yPos))
        animationSpawners
            .map { it.actor }
            .forEach {
                it.setBounds(x, y, width, height)
            }
    }

}

inline fun <reified T : AnimationSpawner> ActorWithAnimationSpawners.findAnimationSpawner(): T? = animationSpawners.find { it is T } as? T

package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.*

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
        val (screenSpaceWidth, screenSpaceHeight) =
            Utils.worldSpaceToScreenSpaceDimensions(worldSpaceBounds.width, worldSpaceBounds.height, screen.viewport)
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
//    var isSelected: Boolean

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
        actor.onEnterEvent { event, x, y ->
            if (!CustomScrollableFlexBox.isInsideScrollableParents(actor, x, y)) return@onEnterEvent
            if (!isHoveredOver) actor.fire(HoverEnterEvent())
            isHoveredOver = true
        }

        actor.onTouchEvent { event, x, y, pointer, button ->
            if (event.type == InputEvent.Type.touchUp) isClicked = true
        } //onTouch needed, since onClick doesn't trigger rightClicks
        actor.onExit {
            if (!isClicked) {
                if (isHoveredOver) actor.fire(HoverLeaveEvent())
                isHoveredOver = false
            }
            isClicked = false
        }
    }
}

interface FocusableActor : HoverStateActor {

    var group: SelectionGroup?

    /**
     * make sure this is only set to false if it is NOT SELECTED
     */
    var isFocusable: Boolean
    var isFocused: Boolean
    var isSelectable: Boolean
    var isSelected: Boolean

    fun bindFocusStateListeners(actor: Actor, screen: OnjScreen) {
        bindHoverStateListeners(actor)
        bindSelectableListener(actor, screen)
        actor.onHoverEnter {
            if (isFocusable) screen.focusedActor = actor
        }
        actor.onHoverLeave {
            if (screen.focusedActor == actor) screen.focusedActor = null
        }
    }

    private fun bindSelectableListener(actor: Actor, screen: OnjScreen) {
        actor.onButtonClick {
            if (actor != it.target) return@onButtonClick //THIS IS VERY IMPORTANT, OR IT GETS EXECUTED MULTIPLE TIMES
            if (actor is DisableActor && actor.isDisabled) return@onButtonClick
            if (actor !is FocusableActor || !actor.isSelectable) return@onButtonClick
            screen.changeSelectionFor(actor)
        }
    }

    fun setFocusableTo(newVal: Boolean, actor: Actor) {
        if (actor !is FocusableActor ) throw RuntimeException("tried to make non Focusable Element focusbale")
        if (actor.isFocused && !newVal && actor is HasOnjScreen) actor.screen.focusedActor = null
        if (newVal) actor.touchable = Touchable.enabled
        actor.isFocusable = newVal
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

interface HasOnjScreen {
    val screen: OnjScreen
}

interface DisplayDetailActor {

    var detailWidget: DetailWidget?

    //TODO maybe add disabled option
    fun <T> registerOnFocusDetailActor(
        actor: T,
        screen: OnjScreen
    ) where T : DisplayDetailActor, T : Actor = screen.addOnFocusDetailActor(actor)

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

interface OnLayoutActor {

    fun onLayout(callback: () -> Unit)
}

inline fun <reified T : AnimationSpawner> ActorWithAnimationSpawners.findAnimationSpawner(): T? =
    animationSpawners.find { it is T } as? T


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

interface KotlinStyledActor : FocusableActor {
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

    fun bindDefaultListeners(actor: Actor, screen: OnjScreen) {
        actor.customClickable()
        bindFocusStateListeners(actor, screen)
    }

    private fun Actor.customClickable() {
        onClickEvent { _, x, y ->
            if (CustomScrollableBox.isInsideScrollableParents(this, x, y)) {
                if (this is DragAndDroppableActor && inDragPreview) return@onClickEvent
                fire(ButtonClickEvent())
            }
        }
    }
}

interface DragAndDroppableActor : FocusableActor {
    var isDraggable: Boolean

    /**
     * the part, where you click on it, and it jumps there, but actually stays in place if you don't move your mouse
     */
    var inDragPreview: Boolean

    /**
     * the groups of the POSSIBLE dragAndDropTargets (the targets themselves can be deactivated via "isSelectable" or "isDisabled")
     */
    var targetGroups: List<String>

    /**
     * when it should execute the reset to the start for dragAndDrop, if null, then [CustomCenteredDragSource.defaultResetCondition] is choosen instead
     */
    var resetCondition: ((Actor?) -> Boolean)?

    /**
     * when it should execute the reset to the start for dragAndDrop, if null, then [CustomCenteredDragSource.defaultResetCondition] is choosen instead
     */
    val onDragAndDrop: MutableList<(Actor, Actor) -> Unit>


    fun <T> makeDraggable(actor: T) where T : Actor, T : DragAndDroppableActor {
        actor.isDraggable = true
        actor.setFocusableTo(true, actor)
        actor.isSelectable = true
    }

    fun bindDragging(actor: Actor, screen: OnjScreen) {
        val dragAndDrops = screen._dragAndDrop
        val group = group
        if (group == null) {
            FortyFiveLogger.warn(
                "DragAndDroppableActor",
                "You tried to bind a source without a group, this will not work"
            )
            return
        }
        val dragAndDrop = dragAndDrops.getOrPut(group) { DragAndDrop() }

//        dragAndDrop?.setTapSquareSize(-1F) //other possibilty for dragAndDrop
        dragAndDrop.addSource(CustomCenteredDragSource(dragAndDrop, actor, screen))

        actor.onSelectChange { _, new, fromMouse ->
            if (!isDraggable) return@onSelectChange
            if ("draggingAnElement" in screen.screenState) return@onSelectChange
            if (isSelected) {
                if (!fromMouse) //current possibilty for dragAndDrop
                    actorDragStarted(actor, screen, false)
            } else {
                if (fromMouse) screen.escapeSelectionHierarchy()
                screen.focusedActor = actor
            }
        }
    }


    fun actorDragStarted(actor: Actor, screen: OnjScreen, fromMouse: Boolean = true) {
        screen.enterState(dragAndDropStateName)
        if (fromMouse) {
            screen.focusedActor = null
        }
        screen.addToSelectionHierarchy(
            FocusableParent(
                onSelection = { it2 ->
                    val target = it2.last()
                    val source = screen.draggedActor ?: it2.first()
                    if (source != target) {
                        onDragAndDrop.forEach { it.invoke(source, target) }
                        if (target is DragAndDroppableActor)
                            target.onDragAndDrop.forEach { it.invoke(source, target) }
                    }
                },
                transitions = listOf(SelectionTransition(groups = targetGroups)),
                onLeave = {
                    screen.leaveState(dragAndDropStateName)
                }
            )
        )
        if ((actor !is FocusableActor || actor.group !in targetGroups) && !fromMouse)
            screen.focusNext()
    }

    fun bindDroppable(actor: Actor, screen: OnjScreen, sourceGroups: List<String>) {
        val dragAndDrops = screen._dragAndDrop
        val group = group
        if (group == null) {
            FortyFiveLogger.warn(
                "DragAndDroppableActor",
                "You need to set the target group, or it will not be accepted as a target"
            )
            return
        }
        sourceGroups.forEach {
            val dragAndDrop = dragAndDrops.getOrPut(it) { DragAndDrop() }
            dragAndDrop.addTarget(CustomDropTarget(actor, screen))
        }
    }

    companion object {
        const val dragAndDropStateName: String = "draggableActor_draggingElement"
    }
}
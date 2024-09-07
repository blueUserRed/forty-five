package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.*
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
//
//    fun bindSubSelectableListener(actor: Actor, screen: OnjScreen) {
//
//        actor.onSelectChange {_,_,it->
//            println(it)
//        }
//    }
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
    var drawOffsetX: Float
    var drawOffsetY: Float
    var logicalOffsetX: Float
    var logicalOffsetY: Float
}

interface HasOnjScreen {
    val screen: OnjScreen
}

interface DisplayDetailsOnHoverActor {

    var actorTemplate: String
    var detailActor: Actor?
    var mainHoverDetailActor: String?
    var isHoverDetailActive: Boolean
    val actor: Actor

    fun <T> registerOnFocusDetailActor(
        actor: T,
        screen: OnjScreen
    ) where T : DisplayDetailsOnHoverActor, T : Actor = screen.addOnFocusDetailActor(actor)

    fun setBoundsOfFocusDetailActor(screen: OnjScreen) {
        val actor = actor
        val detailActor = detailActor
        if (detailActor !is Layout) return
        val prefHeight = detailActor.prefHeight
        val prefWidth = detailActor.prefWidth
        if (mainHoverDetailActor == null || actor !is HasOnjScreen || actor.stage == null) {
            val (x, y) = actor.localToStageCoordinates(Vector2(0f, 0f))
            detailActor.setBounds(
                x + actor.width / 2 - detailActor.width / 2,
                y + actor.height,
                if (prefWidth == 0f) detailActor.width else prefWidth,
                prefHeight
            )
        } else {
            val mainActor = actor.screen.namedActorOrError(mainHoverDetailActor!!)
            val distToRoot = mainActor.localToActorCoordinates(detailActor, Vector2(0F, 0F))
            val actorPos = actor.localToStageCoordinates(Vector2(0, 0))
            val yCoordinate =
                if (actorPos.y + actor.height - distToRoot.y + mainActor.height > actor.stage.viewport.worldHeight) {
                    actorPos.y - mainActor.height - distToRoot.y //if it would be too high up, it will be lower
                } else {
                    actorPos.y + actor.height - distToRoot.y
                }
            detailActor.setBounds(
                (actorPos.x + actor.width / 2 - mainActor.width / 2 - distToRoot.x).between(
                    -distToRoot.x,
                    actor.stage.viewport.worldWidth - distToRoot.x - mainActor.width
                ),
                yCoordinate,
                if (prefWidth == 0f) detailActor.width else prefWidth,
                prefHeight
            )
        }
        detailActor.invalidateHierarchy()
    }

    fun drawFocusDetail(screen: OnjScreen, batch: Batch) {
        detailActor?.draw(batch, 1f)
    }

    fun getFocusDetailData(): Map<String, OnjValue>

    fun onDetailDisplayStarted() {}
    fun onDetailDisplayEnded() {}

}

interface GeneralDisplayDetailOnHoverActor : DisplayDetailsOnHoverActor {

    val additionalHoverData: MutableMap<String, OnjValue>

    override var actorTemplate: String
        get() = "general_hover_detail_template"
        set(value) {}

    override fun setBoundsOfFocusDetailActor(screen: OnjScreen) {
    }

    override fun drawFocusDetail(screen: OnjScreen, batch: Batch) {
        val detailActor = detailActor ?: return
        val (x, y) = actor.localToStageCoordinates(Vector2(0f, 0f))
        if (detailActor is Layout) {
            detailActor.width = detailActor.prefWidth
            detailActor.height = detailActor.prefHeight
        }
        var chosenY = y + actor.height
        if (chosenY + detailActor.height > screen.viewport.worldHeight) {
            chosenY = y - detailActor.height
        }
        detailActor.setPosition(
            x + actor.width / 2 - detailActor.width / 2,
            chosenY,
        )
        detailActor.draw(batch, 1f)
    }
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
            if (CustomScrollableBox.isInsideScrollableParents(this, x, y)){
                if (this is DraggableActor && inDragPreview) return@onClickEvent
                fire(ButtonClickEvent())
            }
        }
    }
}

interface DraggableActor : FocusableActor {
    var isDraggable: Boolean
    var inDragPreview: Boolean
    var targetGroups: List<String>
    fun bindDragging(actor: Actor, screen: OnjScreen) {
        val dragAndDrops = screen._dragAndDrop
        val dragAndDrop = group?.let { dragAndDrops.getOrPut(it) { DragAndDrop() } }

//        dragAndDrop?.setTapSquareSize(-1F) //other possibilty for dragAndDrop
        dragAndDrop?.addSource(CustomCenteredDragSource(dragAndDrop, actor))

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
        screen.enterState("draggableActor_draggingElement")
        if (fromMouse) {
            screen.focusedActor = null
        }
        screen.addToSelectionHierarchy(
            FocusableParent(
                onSelection = {
                    if ((it !is FocusableActor || it.group !in targetGroups) && !fromMouse)
                        screen.focusNext() //select the first of the possible placement options
                },
                transitions = listOf(SelectionTransition(groups = targetGroups)),
                onLeave = {
                    screen.leaveState("draggableActor_draggingElement")
                }
            )
        )
    }
}
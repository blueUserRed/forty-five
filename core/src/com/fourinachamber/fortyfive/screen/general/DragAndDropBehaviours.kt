package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.fourinachamber.fortyfive.game.card.CardDragSource
import com.fourinachamber.fortyfive.game.card.CardDropTarget
import com.fourinachamber.fortyfive.game.card.PutCardsUnderDeckDropTarget
import com.fourinachamber.fortyfive.game.card.RevolverDropTarget
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardDragSource
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardDropTarget
import com.fourinachamber.fortyfive.map.statusbar.BackpackDragSource
import com.fourinachamber.fortyfive.map.statusbar.BackpackDropTarget
import com.fourinachamber.fortyfive.map.statusbar.DeckSlotDropTarget
import com.fourinachamber.fortyfive.screen.general.customActor.OffSettable
import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.eitherLeft
import com.fourinachamber.fortyfive.utils.eitherRight
import com.fourinachamber.fortyfive.utils.obj
import onj.value.OnjNamedObject

object DragAndDropBehaviourFactory {

    private val dragBehaviours: MutableMap<String, DragBehaviourCreator> = mutableMapOf()
    private val dropBehaviours: MutableMap<String, DropBehaviourCreator> = mutableMapOf()

    init {
        dragBehaviours["CardDragSource"] = { dragAndDrop, actor, onj ->
            CardDragSource(dragAndDrop, actor, onj)
        }
        dropBehaviours["RevolverDropTarget"] = { dragAndDrop, actor, onj ->
            RevolverDropTarget(dragAndDrop, actor, onj)
        }
        dropBehaviours["CardDropTarget"] = { dragAndDrop, actor, onj ->
            CardDropTarget(dragAndDrop, actor, onj)
        }
        dragBehaviours["BackpackDragSource"] = { dragAndDrop, actor, onj ->
            BackpackDragSource(dragAndDrop, actor, onj)
        }
        dropBehaviours["DeckSlotDropTarget"] = { dragAndDrop, actor, onj ->
            DeckSlotDropTarget(dragAndDrop, actor, onj)
        }
        dropBehaviours["BackpackDropTarget"] = { dragAndDrop, actor, onj ->
            BackpackDropTarget(dragAndDrop, actor, onj)
        }
        dragBehaviours["ChooseCardDragSource"] = { dragAndDrop, actor, onj ->
            ChooseCardDragSource(dragAndDrop, actor, onj)
        }
        dropBehaviours["ChooseCardDropTarget"] = { dragAndDrop, actor, onj ->
            ChooseCardDropTarget(dragAndDrop, actor, onj)
        }
        dropBehaviours["PutCardsUnderDeckDropTarget"] = { dragAndDrop, actor, onj ->
            PutCardsUnderDeckDropTarget(dragAndDrop, actor, onj)
        }
    }

    fun dragBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour {
        return dragBehaviourOrNull(name, dragAndDrop, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dragBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DragBehaviour? = dragBehaviours[name]?.invoke(dragAndDrop, actor, onj)

    fun dropBehaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour {
        return dropBehaviourOrNull(name, dragAndDrop, actor, onj) ?: run {
            throw RuntimeException("unknown drag behaviour $name")
        }
    }

    private fun dropBehaviourOrNull(
        name: String,
        dragAndDrop: DragAndDrop,
        actor: Actor,
        onj: OnjNamedObject
    ): DropBehaviour? = dropBehaviours[name]?.invoke(dragAndDrop, actor, onj)

    fun behaviourOrError(
        name: String,
        dragAndDrop: DragAndDrop,
        onjScreen: OnjScreen,
        actor: Actor,
        onj: OnjNamedObject
    ): Either<DragBehaviour, DropBehaviour> {
        return dragBehaviourOrNull(name, dragAndDrop, actor, onj)?.eitherLeft() ?: dropBehaviourOrNull(
            name,
            dragAndDrop,
            actor,
            onj
        )?.eitherRight() ?: throw RuntimeException("Unknown drag or drop behaviour: $name")
    }
}


abstract class DragBehaviour(
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Source(actor)

@Suppress("unused", "UNUSED_PARAMETER") // may be necessary in the future, also for symmetry with DragBehaviour
abstract class DropBehaviour(
    protected val dragAndDrop: DragAndDrop,
//    protected val onjScreen: OnjScreen,
    actor: Actor,
    onj: OnjNamedObject
) : DragAndDrop.Target(actor)

/**
 * drags the object in the center
 */
abstract class CenteredDragSource(
    dragAndDrop: DragAndDrop,
    actor: Actor,
    onj: OnjNamedObject,
    showClickHint: Boolean = false
) : DragBehaviour(dragAndDrop, actor, onj) {

    val centerOnClick = OnClickToCenter(this)

    init {
        if (actor is OffSettable && showClickHint) actor.addListener(centerOnClick)
    }

    fun canBeStarted(actor: Actor, x: Float, y: Float): Boolean {
        return CustomScrollableFlexBox.isInsideScrollableParents(actor, x, y)
    }

    open fun fakeStart(event: InputEvent?, x: Float, y: Float, pointer: Int): Boolean = true

    open fun fakeStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {}

    override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.drag(event, x, y, pointer)
        val parentOff = actor.parent.localToStageCoordinates(Vector2(0f, 0f))
        dragAndDrop.setDragActorPosition(
            -parentOff.x + actor.width / 2,
            -parentOff.y - actor.height / 2
        )
        //if there are any errors, it might be because of scaling //see files at commit 17278a0ddd6f821358af53ba331443958292d872
    }

    protected fun startReal() {
        centerOnClick.realStart = true
    }

    override fun dragStop(
        event: InputEvent?,
        x: Float,
        y: Float,
        pointer: Int,
        payload: DragAndDrop.Payload?,
        target: DragAndDrop.Target?
    ) {
        if (payload == null) return
        val obj = payload.obj as ExecutionPayload
        obj.onDragStop()
    }
}

open class ExecutionPayload { //TODO maybe interface, but idk how to set "tasks" default value to mutableListOf()

    /**
     * get executed on DragStop
     */
    val tasks: MutableList<() -> Unit> = mutableListOf()

    /**
     * called when the drag is stopped
     */
    fun onDragStop() {
        for (task in tasks) task()
    }

    /**
     * when the drag is stopped, the actor will be reset to [pos]
     */
    fun resetTo(actor: Actor, pos: Vector2) = tasks.add {
        actor.setPosition(pos.x, pos.y)
    }
}


typealias DragBehaviourCreator = (DragAndDrop, Actor, OnjNamedObject) -> DragBehaviour
typealias DropBehaviourCreator = (DragAndDrop, Actor, OnjNamedObject) -> DropBehaviour

class OnClickToCenter(private val src: CenteredDragSource) : ClickListener() {

    var realStart = false
    private var startOffset: Vector2? = Vector2()
    override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (!src.canBeStarted(src.actor, x, y)) return false
        if (button != 0 || !src.fakeStart(event, x, y, pointer)) return super.touchDown(event, x, y, pointer, button)

        realStart = false
        startOffset = Vector2(
            x * src.actor.scaleX - (src.actor.width / 2),
            y * src.actor.scaleY - (src.actor.height / 2)
        )
        startOffset?.let {
            val actor = src.actor as OffSettable
            actor.drawOffsetX += it.x
            actor.drawOffsetY += it.y
        }
        return super.touchDown(event, x, y, pointer, button)
    }

    override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
        startOffset?.let {
            val actor = src.actor as OffSettable
            actor.drawOffsetX -= it.x
            actor.drawOffsetY -= it.y
            if (!realStart) {
                src.fakeStop(event, x, y, pointer)
            }
        }
        startOffset = null
        super.touchUp(event, x, y, pointer, button)
    }
}

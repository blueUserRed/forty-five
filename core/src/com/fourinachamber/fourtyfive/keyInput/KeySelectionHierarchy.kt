package com.fourinachamber.fourtyfive.keyInput

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fourtyfive.screen.general.KeySelectableActor

class KeySelectionHierarchyNode(
    val children: List<KeySelectionHierarchyNode>,
    val parent: KeySelectionHierarchyNode?,
    val actor: Actor
) {

    val isSelectable: Boolean
        get() = actor is KeySelectableActor

    fun root(): KeySelectionHierarchyNode {
        var current = this
        while (current.parent != null) current = current.parent!!
        return current
    }

    fun getFirstSelectableChild(reverse: Boolean = false): KeySelectionHierarchyNode? {
        val children = if (reverse) children.reversed() else children
        for (child in children) {
            if (child.isSelectable) return child
            val childResult = child.getFirstSelectableChild(reverse)
            if (childResult != null) return childResult
        }
        return null
    }

    fun getSelectableAfterChild(
        after: KeySelectionHierarchyNode?,
        reverse: Boolean = false
    ): KeySelectionHierarchyNode? {
        var hadAfterChild = after == null
        val children = if (reverse) children.reversed() else children
        for (child in children) {
            if (!hadAfterChild) {
                if (child === after) hadAfterChild = true
                continue
            }
            if (child.isSelectable) return child
            val childResult = child.getFirstSelectableChild(reverse)
            if (childResult != null) return childResult
        }
        val parent = parent ?: return null
        return parent.getSelectableAfterChild(this, reverse)
    }

    fun getNextSelectableNode(): KeySelectionHierarchyNode? = getSelectableAfterChild(null)

    fun getPreviousSelectableNode(): KeySelectionHierarchyNode? = getSelectableAfterChild(null, true)

    fun getFirstSelectableNodeInHierarchy(): KeySelectionHierarchyNode? = root().getNextSelectableNode()

    fun getLastSelectableNodeInHierarchy(): KeySelectionHierarchyNode? = root().getPreviousSelectableNode()

    fun getNextOrWrap(): KeySelectionHierarchyNode? = getSelectableAfterChild(this) ?: getFirstSelectableNodeInHierarchy()

    fun getPreviousOrWrap(): KeySelectionHierarchyNode? =
        getSelectableAfterChild(this, true) ?: getLastSelectableNodeInHierarchy()

}

class KeySelectionHierarchyBuilder {

    fun build(root: Actor): KeySelectionHierarchyNode {
        val child = buildChild(root, null)
        return child ?: KeySelectionHierarchyNode(listOf(), null, root)
    }

    private fun buildChild(root: Actor, parent: KeySelectionHierarchyNode?): KeySelectionHierarchyNode? {
        return when (root) {

            is KeySelectableActor -> {
                if (root.partOfHierarchy) {
                    KeySelectionHierarchyNode(listOf(), parent, root)
                } else null
            }

            is Group -> {
                val childNodes = mutableListOf<KeySelectionHierarchyNode>()
                val thisNode = KeySelectionHierarchyNode(childNodes, parent, root)
                for (child in root.children) {
                    val builtChild = buildChild(child, thisNode)
                    builtChild?.let { childNodes.add(it) }
                }
                thisNode
            }

            else -> null

        }
    }

}
package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.KeySelectableActor

/**
 * A tree representing the structure of the screen. Only includes actors that can be selected using the keyboard.
 */
class KeySelectionHierarchyNode(
    val children: List<KeySelectionHierarchyNode>,
    val parent: KeySelectionHierarchyNode?,
    val actor: Actor
) {

    val isSelectable: Boolean
        get() = actor is KeySelectableActor

    /**
     * returns the root of the tree
     */
    fun root(): KeySelectionHierarchyNode {
        var current = this
        while (current.parent != null) current = current.parent!!
        return current
    }

    /**
     * returns the first child of this node that can be selected
     * @param reverse return the last child instead
     */
    fun getFirstSelectableChild(reverse: Boolean = false): KeySelectionHierarchyNode? {
        val children = if (reverse) children.reversed() else children
        for (child in children) {
            if (child.isSelectable) return child
            val childResult = child.getFirstSelectableChild(reverse)
            if (childResult != null) return childResult
        }
        return null
    }

    /**
     * gets the next selectable child that comes after [after]. When this node is out of children, starts searching
     * the rest of the tree.
     * When [after] is null just get the first selectable child
     * @param reverse return the first child before [after] instead
     */
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

    /**
     * finds the next selectable node in the hierarchy, searches the rest of the tree if necessary
     */
    fun getNextSelectableNode(): KeySelectionHierarchyNode? = getSelectableAfterChild(null)

    /**
     * gets the last selectable child
     */
    fun getPreviousSelectableNode(): KeySelectionHierarchyNode? = getSelectableAfterChild(null, true)

    /**
     * finds the first node that can be selected in the tree
     */
    fun getFirstSelectableNodeInHierarchy(): KeySelectionHierarchyNode? = root().getNextSelectableNode()

    /**
     * finds the last node that can be selected in the tree
     */
    fun getLastSelectableNodeInHierarchy(): KeySelectionHierarchyNode? = root().getPreviousSelectableNode()

    /**
     * returns the next selectable node after `this`, if none is found start with the first one again
     */
    fun getNextOrWrap(): KeySelectionHierarchyNode? =
        getSelectableAfterChild(this) ?: getFirstSelectableNodeInHierarchy()

    /**
     * returns the previous selectable node after `this`, if none is found wrap to the last one
     */
    fun getPreviousOrWrap(): KeySelectionHierarchyNode? =
        getSelectableAfterChild(this, true) ?: getLastSelectableNodeInHierarchy()

}

/**
 * creates a tree consisting of [KeySelectionHierarchyNode]s using a tree of actors.
 */
class KeySelectionHierarchyBuilder {

    /**
     * @see KeySelectionHierarchyBuilder
     */
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

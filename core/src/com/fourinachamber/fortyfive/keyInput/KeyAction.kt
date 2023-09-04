package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DetailMapWidget
import com.fourinachamber.fortyfive.map.detailMap.Direction
import com.fourinachamber.fortyfive.screen.gameComponents.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.ButtonClickEvent
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import onj.value.OnjNamedObject
import onj.value.OnjObject

typealias KeyAction = @MainThreadOnly (screen: OnjScreen, event: Keycode?) -> Boolean

/**
 * creates [KeyAction]s using an OnjObject
 */
object KeyActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> KeyAction> = mapOf(

        "ToggleFullscreen" to { obj ->
            val width = obj.get<Long>("width").toInt()
            val height = obj.get<Long>("height").toInt()
            ; { _, _ ->
            if (!Gdx.graphics.isFullscreen) {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            } else {
                Gdx.graphics.setWindowedMode(width, height)
            }
            true
        }
        },

        "SelectCardInHand" to { obj ->
            val num = obj.get<Long>("num").toInt() - 1 // covert from one-indexed to 0-indexed
            lambda@{ screen, _ ->
                val game = FortyFive.currentGame ?: return@lambda false
                val card = game.cardHand.cards.getOrElse(num) { return@lambda false }
                screen.selectedActor = card.actor
                true
            }
        },

        "SelectRevolverSlot" to { obj ->
            var num = obj.get<Long>("num").toInt()
            num = if (num == 5) 5 else 5 - num
            num--
            lambda@{ screen, _ ->
                val game = FortyFive.currentGame ?: return@lambda false
                val slot = game.revolver.slots.getOrElse(num) { return@lambda false }
                screen.selectedActor = slot
                true
            }
        },

        "SelectAdjacent" to { obj ->
            val isLeft = obj.get<String>("direction") == "left"
            lambda@{ screen, _ ->
                val curSelected = screen.selectedActor ?: return@lambda false
                val curGame = FortyFive.currentGame ?: return@lambda false
                when (curSelected) {
                    is CardActor -> {
                        val index = curGame.cardHand.cards.indexOf(curSelected.card)
                        if (index == -1) return@lambda false
                        val adjacentIndex = if (isLeft) index - 1 else index + 1
                        val newSelected = curGame.cardHand.cards.getOrElse(adjacentIndex) { return@lambda false }
                        screen.selectedActor = newSelected.actor
                    }

                    is RevolverSlot -> {
                        val index = curGame.revolver.slots.indexOfFirst { it === curSelected }
                        if (index == -1) return@lambda false
                        val adjacentIndex = if (isLeft) index - 1 else index + 1
                        val newSelected = curGame.revolver.slots.getOrElse(adjacentIndex) { return@lambda false }
                        screen.selectedActor = newSelected
                    }

                    else -> {
                        return@lambda false
                    }
                }
                true
            }
        },

        "PlaceSelectedCardInRevolver" to { obj ->
            var num = obj.get<Long>("revolverSlot").toInt()
            num = if (num == 5) 5 else 5 - num
            lambda@{ screen, _ ->
                val curSelected = screen.selectedActor ?: return@lambda false
                if (curSelected !is CardActor) return@lambda false
                val game = FortyFive.currentGame ?: return@lambda false
                if (num !in 1..5) return@lambda false
                game.loadBulletInRevolver(curSelected.card, num)
                val slot = game.revolver.slots[num - 1]
                screen.selectedActor = slot
                true
            }
        },

        "DeselectAll" to {
            { screen, _ ->
                screen.selectedActor = null
                true
            }
        },

        "NextInHierarchy" to {
            lambda@{ screen, _ ->
                val hierarchy = screen.keySelectionHierarchy ?: return@lambda false
                if (screen.selectedNode == null) {
                    screen.selectedNode = hierarchy.getFirstSelectableNodeInHierarchy()
                } else {
                    val selected = screen.selectedNode!!
                    screen.selectedNode = selected.getNextOrWrap()
                }
                true
            }
        },

        "PreviousInHierarchy" to {
            lambda@{ screen, _ ->
                val hierarchy = screen.keySelectionHierarchy ?: return@lambda false
                if (screen.selectedNode == null) {
                    screen.selectedNode = hierarchy.getLastSelectableNodeInHierarchy()
                } else {
                    val selected = screen.selectedNode!!
                    screen.selectedNode = selected.getPreviousOrWrap()
                }
                true
            }
        },

        "FireClickEvent" to {
            lambda@{ screen, _ ->
                val selected = screen.selectedActor ?: return@lambda false
                selected as Actor
                selected.fire(ButtonClickEvent())
                true
            }
        },

        "MoveInDetailMap" to {
            lambda@{ screen, _ ->
                val targetNode = when (it.get<String>("direction")) {
                    "right" -> MapManager.currentMapNode.getEdge(Direction.RIGHT)
                    "left" -> MapManager.currentMapNode.getEdge(Direction.LEFT)
                    "up" -> MapManager.currentMapNode.getEdge(Direction.UP)
                    "down" -> MapManager.currentMapNode.getEdge(Direction.DOWN)
                    else -> null
                }
                targetNode ?: return@lambda true
                (screen.namedActorOrError(it.get<String>("mapActor")) as DetailMapWidget).moveToNextNode(targetNode)
                true
            }
        },
        "EnterEventDetailMap" to {
            lambda@{ screen, _ ->
                (screen.namedActorOrError(it.get<String>("mapActor")) as DetailMapWidget).onStartButtonClicked()
                true
            }
        }
    )

    /**
     * creates a KeyAction using an OnjObject
     */
    fun getAction(obj: OnjNamedObject): KeyAction {
        return actions[obj.name.removeSuffix("KeyAction")]?.invoke(obj) ?: throw RuntimeException(
            "unknown key action ${obj.name}"
        )
    }
}

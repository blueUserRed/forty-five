package com.fourinachamber.fortyfive.keyInput

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DetailMapWidget
import com.fourinachamber.fortyfive.map.detailMap.Direction
import com.fourinachamber.fortyfive.screen.general.ButtonClickEvent
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import com.fourinachamber.fortyfive.utils.Vector2
import onj.value.OnjNamedObject
import onj.value.OnjObject

typealias KeyAction = @MainThreadOnly (screen: OnjScreen, event: Keycode?) -> Boolean

/**
 * creates [KeyAction]s using an OnjObject
 */
object KeyActionFactory {

    private val onjActions: Map<String, (onj: OnjObject) -> KeyAction> = mapOf(

        "ToggleFullscreen" to { obj ->
            ; { _, _ ->
            OnjScreen.toggleFullScreen()
            true
        }
        },

        "ToggleDebugMenu" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.let {
                    it.showDebugMenu = !it.showDebugMenu
                }
                true
            }
        },

        "NextDebugMenuPage" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.nextDebugPage()
                true
            }
        },

        "PreviousDebugMenuPage" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.previousDebugPage()
                true
            }
        },

        "FireClickEvent" to {
            lambda@{ screen, _ ->
                val actor = it.get<String?>("actor")
                if (actor == null) {
                    val selected = screen.focusedActor ?: return@lambda false
                    selected as Actor
                    selected.fire(ButtonClickEvent())
                    true
                } else {
                    (screen.namedActorOrError(it.get<String>("actor"))).fire(ButtonClickEvent())
                    true
                }
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
        },
    )


    private val kotlinActions: Map<String, (data: Any?) -> KeyAction> = mapOf(
        "ToggleFullscreen" to {
            { _, _ ->
                OnjScreen.toggleFullScreen()
                true
            }
        },


        "ToggleDebugMenu" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.let {
                    it.showDebugMenu = !it.showDebugMenu
                }
                true
            }
        },
        "NextDebugMenuPage" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.nextDebugPage()
                true
            }
        },
        "PreviousDebugMenuPage" to {
            { _, _ ->
                FortyFive.currentRenderPipeline?.previousDebugPage()
                true
            }
        },


        "FocusNext" to {
            lambda@{ screen, _ ->
                screen.focusNext()
                true
            }
        },
        "FocusNextDirectional" to {
            lambda@{ screen, code ->
                val dir = when (code) {
                    Keys.W -> Vector2(0, -1)
                    Keys.A -> Vector2(-1, 0)
                    Keys.S -> Vector2(0, 1)
                    Keys.D -> Vector2(1, 0)
                    else -> null
                }
                screen.focusNext(dir)
                true
            }
        },
        "FocusPrevious" to {
            lambda@{ screen, code ->
                screen.focusPrevious()
                true
            }
        },
        "FocusSpecific" to constructor@{ name ->
            name as? String ?: throw RuntimeException("data provided to 'FocusSpecific' must be of type String")
            return@constructor { screen, code ->
                screen.focusSpecific(name)
                true
            }
        },
        "SelectFocusedElement" to {
            lambda@{ screen, code ->
                val actor = screen.focusedActor
                if (actor == null) {
                    screen.focusNext()
                    return@lambda screen.focusedActor != null
                }
                if (actor !is Actor) return@lambda false
                screen.changeSelectionFor(actor, false)
                true
            }
        },
        "EscapeInSelectionHierarchy" to {
            lambda@{ screen, _ ->
                screen.escapeSelectionHierarchy(false)
                true
            }
        },
    )

    /**
     * creates a KeyAction using an OnjObject
     */
    fun getAction(obj: OnjNamedObject): KeyAction {
        return onjActions[obj.name.removeSuffix("KeyAction")]?.invoke(obj) ?: throw RuntimeException(
            "unknown ONJ key action ${obj.name}"
        )
    }

    /**
     * creates a KeyAction using an OnjObject
     */
    fun getAction(name: String, data: Any? = null): KeyAction {
        return kotlinActions[name]?.invoke(data) ?: throw RuntimeException(
            "unknown KOTLIN key action $name"
        )
    }
}

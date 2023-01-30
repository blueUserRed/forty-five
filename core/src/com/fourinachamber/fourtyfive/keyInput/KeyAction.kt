package com.fourinachamber.fourtyfive.keyInput

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.FourtyFive
import onj.value.OnjNamedObject
import onj.value.OnjObject

typealias KeyAction = () -> Boolean

object KeyActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> KeyAction> = mapOf(

        "ToggleFullscreen" to { obj ->
            val width = obj.get<Long>("width").toInt()
            val height = obj.get<Long>("height").toInt()
            ; {
                if (!Gdx.graphics.isFullscreen) {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
                } else {
                    Gdx.graphics.setWindowedMode(width, height)
                }
                true
            }
        },

        "SelectFirstInCardHand" to { obj ->
            val num = obj.get<Long>("num").toInt() - 1 // covert from one-indexed to 0-indexed
            lambda@ {
                val game = FourtyFive.currentGame ?: return@lambda false
                val card = game.cardHand.cards.getOrElse(num) { return@lambda false }
                game.keySelectedCard = card
                true
            }
        }

    )

    fun getAction(obj: OnjNamedObject): KeyAction {
        return actions[obj.name.removeSuffix("KeyAction")]?.invoke(obj) ?: throw RuntimeException(
            "unknown key action ${obj.name}"
        )
    }

}

package com.fourinachamber.fourtyfive.keyInput

import com.badlogic.gdx.Gdx
import onj.value.OnjNamedObject
import onj.value.OnjObject

typealias KeyAction = () -> Boolean

object KeyActionFactory {

    private val actions: Map<String, (onj: OnjObject) -> KeyAction> = mapOf(

        "ToggleFullscreenKeyAction" to lambda@ { obj ->
            val width = obj.get<Long>("width").toInt()
            val height = obj.get<Long>("height").toInt()
            return@lambda {
                if (!Gdx.graphics.isFullscreen) {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
                } else {
                    Gdx.graphics.setWindowedMode(width, height)
                }
                true
            }
        }

    )

    fun getAction(obj: OnjNamedObject): KeyAction {
        return actions[obj.name]?.invoke(obj) ?: throw RuntimeException(
            "unknown key action ${obj.name}"
        )
    }

}

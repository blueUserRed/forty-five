package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import io.github.orioncraftmc.meditate.YogaNode


abstract class StyleProperty {

    abstract fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen)

}

class BackgroundProperty(
    private val backgroundName: String?
) : StyleProperty() {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen) = when (actor) {

        is CustomImageActor -> actor.drawable = backgroundName?.let {
            screen.drawableOrError(backgroundName)
        }

        is CustomLabel -> actor.background = backgroundName?.let {
            screen.drawableOrError(backgroundName)
        }

        else -> throw RuntimeException(
            "background property cannot be applied to ${actor::class.simpleName}"
        )
    }
}

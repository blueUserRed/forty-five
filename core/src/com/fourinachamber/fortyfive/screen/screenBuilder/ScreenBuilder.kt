package com.fourinachamber.fortyfive.screen.screenBuilder

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import dev.lyze.flexbox.FlexBox

interface ScreenBuilder {

    val name: String

    fun build(controllerContext: Any? = null): OnjScreen

    fun generateFromTemplate(name: String, data: Map<String, Any?>, parent: Group?, screen: OnjScreen): Actor?

    fun addDataToWidgetFromTemplate(
        name: String,
        data: Map<String, Any?>,
        parent: FlexBox?,
        screen: OnjScreen,
        actor: Actor,
        removeOldData: Boolean = true,
    )

}
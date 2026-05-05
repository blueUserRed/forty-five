package com.microwavestudios.fortyfive.screen.screenBuilder

import com.microwavestudios.fortyfive.screen.RenderableScreen

interface ScreenBuilder {

    val name: String

    fun build(controllerContext: Any? = null, previousScreen: RenderableScreen?): RenderableScreen

}
package com.microwavestudios.fortyfive.screen.screenBuilder

import com.microwavestudios.fortyfive.screen.OnjScreen

interface ScreenBuilder {

    val name: String

    fun build(controllerContext: Any? = null, previousScreen: OnjScreen?): OnjScreen

}
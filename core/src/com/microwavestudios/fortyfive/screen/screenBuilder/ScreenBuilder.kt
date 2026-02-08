package com.microwavestudios.fortyfive.screen.screenBuilder

import com.microwavestudios.fortyfive.screen.CustomScreen

interface ScreenBuilder {

    val name: String

    fun build(controllerContext: Any? = null, previousScreen: CustomScreen?): CustomScreen

}